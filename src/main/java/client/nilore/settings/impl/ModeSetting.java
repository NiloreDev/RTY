package client.nilore.settings.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import client.nilore.settings.Setting;
import client.nilore.settings.SettingVisibility;

public class ModeSetting
extends Setting<String> {
    @Getter
    private String[] modes;

    public ModeSetting(String string, String ... stringArray) {
        super(string, stringArray.length > 0 ? stringArray[0] : null);
        this.modes = stringArray;
    }

    public ModeSetting withVisibility(SettingVisibility settingVisibility) {
        this.setVisibility(settingVisibility);
        return this;
    }

    public ModeSetting withDefault(String string) {
        this.setValue(string);
        return this;
    }

    public void setModes(String... modes) {
        this.modes = modes == null ? new String[0] : modes;
        if (this.modes.length == 0) {
            this.setValue(null);
            return;
        }
        boolean found = false;
        for (String mode : this.modes) {
            if (mode != null && mode.equals(this.getValue())) {
                found = true;
                break;
            }
        }
        if (!found) {
            this.setValue(this.modes[0]);
        }
    }

    public boolean is(String string) {
        return this.getValue() != null && this.getValue().equals(string);
    }

    @Override
    public void save(JsonObject jsonObject) {
        jsonObject.addProperty(this.getName(), this.getValue());
    }

    @Override
    public void load(JsonElement jsonElement) {
        this.setValue(jsonElement.getAsString());
    }

    }