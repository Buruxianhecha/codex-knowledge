import SwiftUI

@main
struct HuaiminApp: App {
    @StateObject private var store = AppStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .preferredColorScheme(.dark)
        }
    }
}

private struct OceanBackground: View {
    var body: some View {
        GeometryReader { frame in
            Image("wallpaper_5")
                .resizable()
                .scaledToFill()
                .frame(width: frame.size.width, height: frame.size.height)
                .clipped()
                .overlay(Color.black.opacity(0.22))
        }
        .ignoresSafeArea()
    }
}

struct RootView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        ZStack {
            OceanBackground()
            TabView {
                ConversationListView()
                    .tabItem { Label("聊天", systemImage: "bubble.left.and.bubble.right") }
                ContactsView()
                    .tabItem { Label("通讯录", systemImage: "person.crop.rectangle.stack") }
                DiscoverView()
                    .tabItem { Label("发现", systemImage: "safari") }
                ProfileView()
                    .tabItem { Label("主页", systemImage: "person.crop.circle") }
            }
            .tint(Color(red: 0.45, green: 0.8, blue: 0.92))
        }
        .alert("本地存储提醒", isPresented: Binding(
            get: { store.storageError != nil },
            set: { if !$0 { store.storageError = nil } }
        )) {
            Button("知道了") { store.storageError = nil }
        } message: {
            Text(store.storageError ?? "")
        }
    }
}

private struct CompanionAvatar: View {
    let name: String

    var body: some View {
        ZStack {
            Circle().fill(.cyan.opacity(0.20))
            Circle().strokeBorder(.white.opacity(0.20), lineWidth: 1)
            Text(String(name.prefix(1)))
                .font(.headline)
                .foregroundStyle(.white)
        }
        .frame(width: 48, height: 48)
    }
}

struct ConversationListView: View {
    @EnvironmentObject private var store: AppStore
    @State private var showCreate = false

    var body: some View {
        NavigationStack {
            List {
                if store.companions.isEmpty {
                    ContentUnavailableView(
                        "还没有 AI 角色",
                        systemImage: "person.crop.circle.badge.plus",
                        description: Text("先到通讯录创建角色并设置 API Key")
                    )
                    .listRowBackground(Color.clear)
                }
                ForEach(store.companions) { companion in
                    NavigationLink {
                        ChatDetailView(companionID: companion.id)
                    } label: {
                        HStack(spacing: 12) {
                            CompanionAvatar(name: companion.name)
                            VStack(alignment: .leading, spacing: 5) {
                                Text(companion.name).font(.headline)
                                Text(store.messages(for: companion.id).last?.text ?? "开始聊天")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                        .padding(.vertical, 5)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(OceanBackground())
            .toolbarBackground(.hidden, for: .navigationBar)
            .navigationTitle("怀民亦未寝")
            .toolbar {
                Button {
                    showCreate = true
                } label: { Image(systemName: "plus") }
                    .accessibilityLabel("创建 AI 角色")
            }
            .sheet(isPresented: $showCreate) { CompanionEditorView() }
        }
    }
}

struct ContactsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var showCreate = false

    var body: some View {
        NavigationStack {
            List {
                Section("AI 联系人") {
                    ForEach(store.companions) { companion in
                        NavigationLink {
                            ChatDetailView(companionID: companion.id)
                        } label: {
                            HStack(spacing: 12) {
                                CompanionAvatar(name: companion.name)
                                VStack(alignment: .leading) {
                                    Text(companion.name).font(.headline)
                                    Text(companion.service.rawValue + " · " + companion.model)
                                        .font(.caption).foregroundStyle(.secondary)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                    if store.companions.isEmpty {
                        Text("点击右上角 ＋ 创建你的第一个 AI 角色")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(OceanBackground())
            .toolbarBackground(.hidden, for: .navigationBar)
            .navigationTitle("通讯录")
            .toolbar {
                Button {
                    showCreate = true
                } label: { Image(systemName: "plus") }
                    .accessibilityLabel("创建 AI 角色")
            }
            .sheet(isPresented: $showCreate) { CompanionEditorView() }
        }
    }
}

struct ChatDetailView: View {
    @EnvironmentObject private var store: AppStore
    let companionID: UUID
    @State private var draft = ""
    @State private var isSending = false
    @State private var errorText: String?
    @State private var showEditor = false

    private var companion: Companion? {
        store.companions.first { $0.id == companionID }
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        ForEach(store.messages(for: companionID)) { message in
                            HStack {
                                if message.role == .user { Spacer(minLength: 55) }
                                Text(message.text)
                                    .padding(.horizontal, 15)
                                    .padding(.vertical, 11)
                                    .foregroundStyle(.white)
                                    .background(
                                        message.role == .user
                                        ? Color(red: 0.10, green: 0.40, blue: 0.56)
                                        : Color(red: 0.15, green: 0.22, blue: 0.35),
                                        in: RoundedRectangle(cornerRadius: 17)
                                    )
                                    .textSelection(.enabled)
                                if message.role == .assistant { Spacer(minLength: 55) }
                            }
                            .id(message.id)
                        }
                        if isSending {
                            HStack(spacing: 8) {
                                ProgressView()
                                Text("TA 正在回复…").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                    .padding(14)
                }
                .defaultScrollAnchor(.bottom)
                .onChange(of: store.messages(for: companionID).count) { _, _ in
                    if let last = store.messages(for: companionID).last {
                        withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                    }
                }
            }
            Divider()
            HStack(alignment: .bottom, spacing: 10) {
                TextField("发送消息…", text: $draft, axis: .vertical)
                    .lineLimit(1...5)
                    .padding(11)
                    .background(.white.opacity(0.10), in: RoundedRectangle(cornerRadius: 18))
                Button {
                    Task { await send() }
                } label: {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.system(size: 31))
                }
                .disabled(isSending || draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .accessibilityLabel("发送消息")
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
        }
        .background(OceanBackground())
        .navigationTitle(companion?.name ?? "聊天")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let companion {
                Button { showEditor = true } label: {
                    Image(systemName: "person.crop.circle.badge.checkmark")
                }
                .accessibilityLabel("设置 AI")
                .sheet(isPresented: $showEditor) { CompanionEditorView(companion: companion) }
            }
        }
        .alert("消息发送失败", isPresented: Binding(
            get: { errorText != nil },
            set: { if !$0 { errorText = nil } }
        )) {
            Button("知道了") { errorText = nil }
        } message: {
            Text(errorText ?? "")
        }
    }

    @MainActor private func send() async {
        guard let companion, !isSending else { return }
        let text = draft
        draft = ""
        isSending = true
        defer { isSending = false }
        do {
            try await store.send(text, to: companion)
        } catch {
            errorText = error.localizedDescription
        }
    }
}

struct DiscoverView: View {
    var body: some View {
        NavigationStack {
            List {
                Section {
                    Label("朋友圈 · iOS 移植中", systemImage: "photo.on.rectangle.angled")
                    Label("收藏 · iOS 移植中", systemImage: "bookmark")
                } header: {
                    Text("发现")
                } footer: {
                    Text("此阶段不会把空页面伪装成已实现的朋友圈。Android 0.38.2 的数据与权限规则将在后续移植。")
                }
                Section("接下来的功能") {
                    Label("多角色群聊", systemImage: "person.3")
                    Label("日记与待办", systemImage: "book.closed")
                    Label("语音与电话", systemImage: "waveform")
                }
            }
            .scrollContentBackground(.hidden)
            .background(OceanBackground())
            .toolbarBackground(.hidden, for: .navigationBar)
            .navigationTitle("发现")
        }
    }
}

struct ProfileView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        NavigationStack {
            List {
                Section("iOS 移植版") {
                    LabeledContent("构建版本", value: "0.1.0 · 基于安卓 0.38.2")
                    LabeledContent("AI 联系人", value: String(store.companions.count))
                    LabeledContent("聊天记录", value: String(store.messagesByCompanion.values.reduce(0) { $0 + $1.count }))
                }
                Section("隐私与说明") {
                    Text("API Key 保存于 iOS Keychain，不写入聊天记录文件；聊天与角色仅保存在本机。")
                    Text("目前属于 iOS 第一阶段：单聊已接入真实模型服务，群聊/朋友圈/备份/语音尚未移植。")
                    Text("发送消息会直接请求你配置的模型服务商，请确认 Key 对应的费用和隐私条款。")
                }
            }
            .scrollContentBackground(.hidden)
            .background(OceanBackground())
            .toolbarBackground(.hidden, for: .navigationBar)
            .navigationTitle("主页")
        }
    }
}
