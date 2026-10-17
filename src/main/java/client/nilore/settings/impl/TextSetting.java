package client.nilore.settings.impl;

import client.nilore.settings.Setting;
import client.nilore.settings.SettingVisibility;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class TextSetting extends Setting<String> {

    public TextSetting(String name, String defaultValue) {
        super(name, defaultValue);
    }

    /*
     * 支持：
     *
     * new TextSetting(...)
     *     .withVisibility(() -> mode.is("Custom"));
     */
    public TextSetting withVisibility(
            SettingVisibility visibility
    ) {
        this.setVisibility(visibility);
        return this;
    }

    @Override
    public void save(JsonObject jsonObject) {
        jsonObject.addProperty(
                this.getName(),
                this.getValue()
        );
    }

    @Override
    public void load(JsonElement jsonElement) {
        if (jsonElement == null
                || jsonElement.isJsonNull()) {
            return;
        }

        this.setValue(
                jsonElement.getAsString()
        );
    }
}