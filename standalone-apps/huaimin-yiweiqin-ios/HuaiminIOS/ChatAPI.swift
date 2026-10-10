import Foundation

enum ChatFailure: LocalizedError {
    case invalidEndpoint
    case missingModel
    case missingKey
    case http(Int, String)
    case emptyReply

    var errorDescription: String? {
        switch self {
        case .invalidEndpoint: "接口地址必须是有效的 HTTPS 聊天完成接口"
        case .missingModel: "请先填写有效的模型 ID"
        case .missingKey: "请先给这位 AI 配置 API Key"
        case let .http(code, message): "接口请求失败（HTTP \(code)）：\(message)"
        case .emptyReply: "模型没有返回可显示的文字；请核对模型是否兼容 Chat Completions 接口"
        }
    }
}

enum ChatAPI {
    /// First vertical slice: OpenAI-compatible non-streaming text calls.
    /// API calls use the role's selected endpoint and model, never embedded credentials.
    static func reply(for companion: Companion, key: String, history: [ChatMessage]) async throws -> String {
        guard let components = URLComponents(string: companion.endpoint),
              components.scheme?.lowercased() == "https",
              let host = components.host, !host.isEmpty,
              components.user == nil, components.password == nil,
              let url = components.url else { throw ChatFailure.invalidEndpoint }
        guard !companion.model.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else { throw ChatFailure.missingModel }
        guard !key.isEmpty else { throw ChatFailure.missingKey }

        var messages: [[String: String]] = [
            ["role": "system", "content": "你是\(companion.name)。\n\(companion.persona)\n自然交流，不要编造已完成的手机操作。"]
        ]
        for item in history.suffix(30) {
            messages.append(["role": item.role.rawValue, "content": item.text])
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 75
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "model": companion.model,
            "messages": messages,
            "stream": false
        ])

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw ChatFailure.http(0, "服务器未返回 HTTP 响应")
        }
        guard (200...299).contains(http.statusCode) else {
            var reason = "请检查地址、模型、密钥和额度"
            if let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let err = object["error"] as? [String: Any],
               let message = err["message"] as? String {
                reason = String(message.prefix(280))
            }
            throw ChatFailure.http(http.statusCode, reason)
        }
        guard let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let choices = object["choices"] as? [[String: Any]],
              let message = choices.first?["message"] as? [String: Any],
              let text = message["content"] as? String,
              !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw ChatFailure.emptyReply
        }
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
