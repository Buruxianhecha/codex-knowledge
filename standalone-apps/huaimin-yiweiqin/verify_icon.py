#!/usr/bin/env python3
"""Reject undecodable artwork and verify the icon in the actual built APK."""

import argparse
import hashlib
import io
from pathlib import Path

from PIL import Image

ANDROID = "{http://schemas.android.com/apk/res/android}"


def verify_image(data: bytes, description: str) -> tuple[int, int]:
    try:
        with Image.open(io.BytesIO(data)) as image:
            if image.format != "JPEG":
                raise ValueError(f"expected JPEG, got {image.format}")
            image.verify()
        with Image.open(io.BytesIO(data)) as image:
            image.load()
            width, height = image.size
            if width != height or width < 48:
                raise ValueError(f"expected a square launcher icon, got {image.size}")
            return width, height
    except Exception as error:
        raise ValueError(f"{description}: icon cannot be decoded: {error}") from error


def verify_source(source: Path) -> bytes:
    data = source.read_bytes()
    size = verify_image(data, str(source))
    print(f"Source icon decoded: JPEG {size[0]}x{size[1]}, sha256={hashlib.sha256(data).hexdigest()}")
    return data


def verify_apk(
    apk_path: Path,
    expected: bytes,
    expected_package: str = "com.lin.huaimin",
    expected_app_name: str = "怀民亦未寝",
) -> None:
    from loguru import logger

    logger.remove()
    from androguard.core.apk import APK

    apk = APK(str(apk_path))
    if not apk.is_valid_APK() or apk.get_package() != expected_package:
        raise ValueError("APK is invalid or has the wrong application ID")
    if apk.get_app_name() != expected_app_name:
        raise ValueError("APK has the wrong app name")

    manifest = apk.get_android_manifest_xml()
    application = manifest.find("application")
    if application is None:
        raise ValueError("APK has no application element")
    resources = apk.get_android_resources()
    paths = set()

    def check_reference(reference: str | None, label: str) -> None:
        if not reference or not reference.startswith("@"):
            raise ValueError(f"{label}: missing compiled resource reference")
        resolved = resources.get_resolved_res_configs(int(reference[1:], 16))
        if not resolved:
            raise ValueError(f"{label}: resource does not resolve")
        for config, path in resolved:
            if not isinstance(path, str) or Path(path).name != "huaimin_app_icon.jpg":
                raise ValueError(f"{label}: unexpected icon resource {path!r}")
            data = apk.get_file(path)
            size = verify_image(data, f"{apk_path}:{path}")
            if data != expected:
                raise ValueError(f"{label}: packaged artwork differs from the verified source")
            print(f"{label}: {reference} -> {path} ({config.get_qualifier()}, {size[0]}x{size[1]})")
            paths.add(path)

    check_reference(application.get(ANDROID + "icon"), "application.icon")
    check_reference(application.get(ANDROID + "roundIcon"), "application.roundIcon")

    launchers = []
    for component in list(application.findall("activity")) + list(application.findall("activity-alias")):
        for intent_filter in component.findall("intent-filter"):
            actions = {node.get(ANDROID + "name") for node in intent_filter.findall("action")}
            categories = {node.get(ANDROID + "name") for node in intent_filter.findall("category")}
            if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories:
                launchers.append(component)
                reference = component.get(ANDROID + "icon")
                if reference:
                    check_reference(reference, "launcher.icon")
                else:
                    print(f"Launcher {component.get(ANDROID + 'name')} inherits the verified application icon")
    if not launchers:
        raise ValueError("APK has no launcher activity")
    if apk.get_app_icon() not in paths:
        raise ValueError("Android's resolved app icon differs from the verified resources")
    print(f"Built APK icon verified: {apk_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--expected-package", default="com.lin.huaimin")
    parser.add_argument("--expected-app-name", default="怀民亦未寝")
    args = parser.parse_args()
    artwork = verify_source(args.source)
    if args.apk:
        verify_apk(args.apk, artwork, args.expected_package, args.expected_app_name)
