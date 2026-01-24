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
    containerEl.createEl("h2", { text: "MarkdownBrain Settings" });

    new Setting(containerEl)
      .setName("Server URL")
      .setDesc("MarkdownBrain server API base URL")
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
      .setDesc("Publish Key from MarkdownBrain Console")
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
      .setName("Test connection")
      .setDesc("Test connectivity to the server (30s timeout)")
      .addButton((button) =>
        button.setButtonText("Test").onClick(async () => {
          button.setDisabled(true);
          button.setButtonText("Testing...");

          new Notice("Testing connection...");

          const result = await this.plugin.syncClient.getVaultInfo();

          button.setDisabled(false);
          button.setButtonText("Test");

          if (result.success && result.vault) {
            new Notice(
              `Connection successful.\n` +
                `Vault: ${result.vault.name}\n` +
                `Domain: ${result.vault.domain || "Not set"}`,
              5000,
            );
          } else {
            new Notice(`Connection failed: ${result.error || "Unknown error"}`, 5000);
          }
        }),
      );

    new Setting(containerEl)
      .setName("Full sync")
      .setDesc("Manually sync all Markdown files to the server")
      .addButton((button) =>
        button.setButtonText("Start sync").onClick(async () => {
          button.setDisabled(true);
          button.setButtonText("Syncing...");

          await this.plugin.fullSync();

          button.setDisabled(false);
          button.setButtonText("Start sync");
        }),
      );

    new Setting(containerEl)
      .setName("Auto sync")
      .setDesc("Automatically sync on file changes")
      .addToggle((toggle) =>
        toggle.setValue(this.plugin.settings.autoSync).onChange(async (value) => {
          this.plugin.settings.autoSync = value;
          await this.plugin.saveSettings();
        }),
      );
  }
}
