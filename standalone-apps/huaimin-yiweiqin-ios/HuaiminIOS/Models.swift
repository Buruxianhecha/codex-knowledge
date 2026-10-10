import Foundation

// v0.38.2 Android feature model adapted for iOS; identities use stable UUIDs in
// this new iOS store. No Android Room data is silently imported or discarded.
enum AIService: String, CaseIterable, Identifiable, Codable {
    case deepSeek = "DeepSeek"
    case zhipu = "智谱"
    case openAI = "OpenAI"
    case siliconFlow = "硅基流动"
    case kimi = "Kimi"
    case suixiang = "随想"
    case custom = "自定义"

    var id: String { rawValue }

    var endpoint: String {
        switch self {
        case .deepSeek: "https://api.deepseek.com/v1/chat/completions"
        case .zhipu: "https://open.bigmodel.cn/api/paas/v4/chat/completions"
        case .openAI: "https://api.openai.com/v1/chat/completions"
        case .siliconFlow: "https://api.siliconflow.cn/v1/chat/completions"
        case .kimi: "https://api.moonshot.cn/v1/chat/completions"
        case .suixiang: "https://www.sui-xiang.net/v1/chat/completions"
        case .custom: ""
        }
    }

    var suggestedModel: String {
        switch self {
        case .deepSeek: "deepseek-chat"
        case .zhipu: "glm-4-flash"
        case .openAI: "gpt-4o-mini"
        case .siliconFlow, .kimi, .suixiang, .custom: ""
        }
    }
}

struct Companion: Codable, Identifiable, Equatable {
    var id: UUID
    var name: String
    var persona: String
    var service: AIService
    var endpoint: String
    var model: String
    var createdAt: Date

    init(id: UUID = UUID(), name: String, persona: String, service: AIService,
         endpoint: String, model: String, createdAt: Date = Date()) {
        self.id = id
        self.name = name
        self.persona = persona
        self.service = service
        self.endpoint = endpoint
        self.model = model
        self.createdAt = createdAt
    }
}

enum Speaker: String, Codable {
    case user
    case assistant
}

struct ChatMessage: Codable, Identifiable, Equatable {
    var id: UUID = UUID()
    var role: Speaker
    var text: String
    var createdAt: Date = Date()
}

struct SavedData: Codable {
    var companions: [Companion] = []
    var messagesByCompanion: [String: [ChatMessage]] = [:]
}
