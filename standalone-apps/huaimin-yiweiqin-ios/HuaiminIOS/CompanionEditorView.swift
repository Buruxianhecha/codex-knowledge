import SwiftUI

struct CompanionEditorView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss

    private let existing: Companion?
    @State private var name: String
    @State private var persona: String
    @State private var service: AIService
    @State private var endpoint: String
    @State private var model: String
    @State private var newKey: String = ""
    @State private var errorText: String?

    init(companion: Companion? = nil) {
        existing = companion
        _name = State(initialValue: companion?.name ?? "")
        _persona = State(initialValue: companion?.persona ?? "")
        _service = State(initialValue: companion?.service ?? .deepSeek)
        _endpoint = State(initialValue: companion?.endpoint ?? AIService.deepSeek.endpoint)
        _model = State(initialValue: companion?.model ?? AIService.deepSeek.suggestedModel)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("人物") {
                    TextField("名字", text: $name)
                        .textInputAutocapitalization(.never)
                    TextField("人物性格和背景（可多行）", text: $persona, axis: .vertical)
                        .lineLimit(3...8)
                }
                Section("模型服务") {
                    Picker("服务商", selection: $service) {
                        ForEach(AIService.allCases) { item in
                            Text(item.rawValue).tag(item)
                        }
                    }
                    TextField("模型 ID", text: $model)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("完整 HTTPS chat/completions 地址", text: $endpoint, axis: .vertical)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .lineLimit(2...4)
                    SecureField(existing == nil ? "API Key" : "新 API Key（留空保持原值）", text: $newKey)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                Section {
                    Text("API Key 使用 iOS Keychain 独立保存，角色数据使用本机文件存储。")
                    Text("首次移植仅支持 Chat Completions 兼容文本回复；其它模型协议和工具调用需要后续适配。")
                    if existing != nil {
                        Text("如切换服务商却不提供新 Key，原 Key 不会发送给新服务商。")
                    }
                } header: {
                    Text("安全说明")
                }
            }
            .navigationTitle(existing == nil ? "创建 AI" : "编辑 AI")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存", action: save)
                        .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                  || model.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                  || endpoint.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .onChange(of: service) { _, selected in
                endpoint = selected.endpoint
                model = selected.suggestedModel
                newKey = ""
            }
            .alert("无法保存角色", isPresented: Binding(
                get: { errorText != nil },
                set: { if !$0 { errorText = nil } }
            )) {
                Button("知道了") { errorText = nil }
            } message: {
                Text(errorText ?? "")
            }
        }
    }

    private func save() {
        let cleanEndpoint = endpoint.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URLComponents(string: cleanEndpoint),
              url.scheme?.lowercased() == "https", url.host != nil,
              url.user == nil, url.password == nil else {
            errorText = "请填写有效的 HTTPS 聊天完成接口地址"
            return
        }
        let role = Companion(
            id: existing?.id ?? UUID(),
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            persona: persona,
            service: service,
            endpoint: cleanEndpoint,
            model: model.trimmingCharacters(in: .whitespacesAndNewlines),
            createdAt: existing?.createdAt ?? Date()
        )
        let key: String
        if newKey.isEmpty, let existing, existing.service == service,
           existing.endpoint == cleanEndpoint {
            key = store.apiKey(for: existing.id)
        } else {
            key = newKey
        }
        do {
            try store.saveCompanion(role, key: key)
            dismiss()
        } catch {
            errorText = error.localizedDescription
        }
    }
}
