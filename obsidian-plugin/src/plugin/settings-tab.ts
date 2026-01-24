import { type App, Notice, PluginSettingTab, Setting } from "obsidian";
import type MarkdownBrainPlugin from "../main";

export class MarkdownBrainSettingTab extends PluginSettingTab {
  plugin: MarkdownBrainPlugin;

  constructor(app: App, plugin: MarkdownBrainPlugin) {
    super(app, plugin);
    this.plugin = plugin;
  }

  display(): void {
    const { containerEl } = this;

    containerEl.empty();
    containerEl.createEl("h2", { text: "MarkdownBrain 设置" });

    new Setting(containerEl)
      .setName("服务器地址")
      .setDesc("MarkdownBrain 服务器 API 地址")
      .addText((text) =>
        text
          .setPlaceholder("https://api.markdownbrain.com")
          .setValue(this.plugin.settings.serverUrl)
          .onChange(async (value) => {
            this.plugin.settings.serverUrl = value;
            await this.plugin.saveSettings();
          }),
      );

    new Setting(containerEl)
      .setName("Publish Key")
      .setDesc("从 Console 获取的发布密钥（Publish Key）")
      .addText((text) =>
        text
          .setPlaceholder("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
          .setValue(this.plugin.settings.publishKey)
          .onChange(async (value) => {
            this.plugin.settings.publishKey = value;
            await this.plugin.saveSettings();
          }),
      );

    new Setting(containerEl)
      .setName("测试连接")
      .setDesc("测试与服务器的连接（30秒超时）")
      .addButton((button) =>
        button.setButtonText("测试").onClick(async () => {
          button.setDisabled(true);
          button.setButtonText("测试中...");

          new Notice("正在测试连接...");

          const result = await this.plugin.syncClient.getVaultInfo();

          button.setDisabled(false);
          button.setButtonText("测试");

          if (result.success && result.vault) {
            new Notice(
              `✅ 连接成功！\n` +
                `Vault: ${result.vault.name}\n` +
                `Domain: ${result.vault.domain || "未设置"}`,
              5000,
            );
          } else {
            new Notice(`❌ 连接失败: ${result.error || "Unknown error"}`, 5000);
          }
        }),
      );

    new Setting(containerEl)
      .setName("批量同步")
      .setDesc("手动同步所有 Markdown 文件到服务器")
      .addButton((button) =>
        button.setButtonText("开始同步").onClick(async () => {
          button.setDisabled(true);
          button.setButtonText("同步中...");

          await this.plugin.fullSync();

          button.setDisabled(false);
          button.setButtonText("开始同步");
        }),
      );

    new Setting(containerEl)
      .setName("自动同步")
      .setDesc("文件修改时自动同步到服务器")
      .addToggle((toggle) =>
        toggle.setValue(this.plugin.settings.autoSync).onChange(async (value) => {
          this.plugin.settings.autoSync = value;
          await this.plugin.saveSettings();
        }),
      );
  }
}
