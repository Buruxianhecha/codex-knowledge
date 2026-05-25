"""
Multi-engine extractor base class — register engines, run all, pick best.

Usage:
    extractor = MultiEngineExtractor()
    extractor.register(engine_a, score_fn=lambda r: len(r))
    extractor.register(engine_b, score_fn=lambda r: r.confidence)
    result = extractor.extract(input_data)
"""
from typing import Any, Callable


class MultiEngineExtractor:
    def __init__(self):
        self._engines: list[tuple[Any, Callable]] = []

    def register(self, engine, score_fn: Callable[[Any], float]):
        """Register an engine with its scoring function (higher = better)."""
        self._engines.append((engine, score_fn))

    def extract(self, input_data) -> tuple[Any, float] | None:
        """Run all engines, return (best_result, best_score)."""
        best_result, best_score = None, -1.0
        for engine, score_fn in self._engines:
            try:
                result = engine.run(input_data)
                score = score_fn(result)
                if score > best_score:
                    best_result, best_score = result, score
            except Exception:
                continue
        return (best_result, best_score) if best_result is not None else None
