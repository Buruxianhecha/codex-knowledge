import Foundation
import Security
import Combine

enum StorageFailure: LocalizedError {
    case write(String)
    case keychain(OSStatus)

    var errorDescription: String? {
        switch self {
        case let .write(reason): "本地数据保存失败：\(reason)"
        case let .keychain(status): "API Key 安全存储失败（OSStatus \(status)）"
        }
    }
}

private enum APISecret {
    static let service = "com.lin.huaimin.ios.companion.api-key"

    static func read(_ id: UUID) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: id.uuidString,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecReturnData as String: true
        ]
        var found: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &found) == errSecSuccess,
              let bytes = found as? Data else { return nil }
        return String(data: bytes, encoding: .utf8)
    }

    static func write(_ value: String, id: UUID) throws {
        let identity: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: id.uuidString
        ]
        let oldValue = read(id)
        if oldValue == value { return }
        let delete = SecItemDelete(identity as CFDictionary)
        guard delete == errSecSuccess || delete == errSecItemNotFound else {
            throw StorageFailure.keychain(delete)
        }
        guard !value.isEmpty else { return }
        var entry = identity
        entry[kSecValueData as String] = Data(value.utf8)
        entry[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let status = SecItemAdd(entry as CFDictionary, nil)
        guard status == errSecSuccess else { throw StorageFailure.keychain(status) }
    }

    static func delete(_ id: UUID) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: id.uuidString
        ]
        SecItemDelete(query as CFDictionary)
    }
}

@MainActor
final class AppStore: ObservableObject {
    @Published private(set) var companions: [Companion] = []
    @Published private(set) var messagesByCompanion: [String: [ChatMessage]] = [:]
    @Published var storageError: String?

    private var fileURL: URL {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return documents.appendingPathComponent("huaimin-ios-data.json")
    }

    init() {
        do {
            let data = try Data(contentsOf: fileURL)
            let snapshot = try JSONDecoder().decode(SavedData.self, from: data)
            companions = snapshot.companions
            messagesByCompanion = snapshot.messagesByCompanion
        } catch {
            if FileManager.default.fileExists(atPath: fileURL.path) {
                storageError = "本地数据无法读取，请不要卸载应用：\(error.localizedDescription)"
            }
        }
    }

    func messages(for id: UUID) -> [ChatMessage] {
        messagesByCompanion[id.uuidString] ?? []
    }

    func apiKey(for id: UUID) -> String { APISecret.read(id) ?? "" }

    func saveCompanion(_ companion: Companion, key: String) throws {
        let old = companions
        if let index = companions.firstIndex(where: { $0.id == companion.id }) {
            companions[index] = companion
        } else {
            companions.append(companion)
        }
        do {
            try persist()
            try APISecret.write(key.trimmingCharacters(in: .whitespacesAndNewlines), id: companion.id)
        } catch {
            companions = old
            try? persist()
            throw error
        }
    }

    func removeCompanion(_ id: UUID) throws {
        let oldCompanions = companions
        let oldMessages = messagesByCompanion
        companions.removeAll { $0.id == id }
        messagesByCompanion.removeValue(forKey: id.uuidString)
        do {
            try persist()
            APISecret.delete(id)
        } catch {
            companions = oldCompanions
            messagesByCompanion = oldMessages
            throw error
        }
    }

    func send(_ text: String, to companion: Companion) async throws {
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        let key = apiKey(for: companion.id)
        guard !key.isEmpty else { throw ChatFailure.missingKey }
        let previous = messages(for: companion.id)
        let mine = ChatMessage(role: .user, text: clean)
        messagesByCompanion[companion.id.uuidString, default: []].append(mine)
        do {
            try persist()
            let answer = try await ChatAPI.reply(
                for: companion, key: key, history: messages(for: companion.id)
            )
            messagesByCompanion[companion.id.uuidString, default: []].append(
                ChatMessage(role: .assistant, text: answer)
            )
            try persist()
        } catch {
            // Keep the user's message, but never fabricate an AI answer.
            if messages(for: companion.id) == previous { storageError = error.localizedDescription }
            throw error
        }
    }

    private func persist() throws {
        do {
            let data = try JSONEncoder().encode(
                SavedData(companions: companions, messagesByCompanion: messagesByCompanion)
            )
            try data.write(to: fileURL, options: .atomic)
            try FileManager.default.setAttributes(
                [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
                ofItemAtPath: fileURL.path
            )
        } catch {
            storageError = error.localizedDescription
            throw StorageFailure.write(error.localizedDescription)
        }
    }
}
