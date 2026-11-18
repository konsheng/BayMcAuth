package com.baymc.auth.backend;

import com.baymc.auth.BayMcAuth;
import com.baymc.auth.Settings;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

public abstract class Endpoint {

  protected final BayMcAuth plugin;
  protected String type;
  protected String username;

  public Endpoint(BayMcAuth plugin) {
    this.plugin = plugin;
  }

  public Endpoint(BayMcAuth plugin, String type, String username) {
    this.plugin = plugin;
    this.type = type;
    this.username = username;
  }

  public void write(ByteArrayDataOutput output) {
    output.writeUTF(this.type);
    if (!this.type.equals("available_endpoints") && !Settings.IMP.MAIN.BACKEND_API.ENABLED_ENDPOINTS.contains(this.type)) {
      output.writeInt(-1);
      output.writeUTF(this.username);
      return;
    }

    output.writeInt(1);
    output.writeUTF(Settings.IMP.MAIN.BACKEND_API.TOKEN);
    output.writeUTF(this.username);
    this.writeContents(output);
  }

  public void read(ByteArrayDataInput input) {
    int version = input.readInt();
    if (version != 0) {
      throw new IllegalStateException("后端接口 '" + this.type + "' 不支持协议版本: " + version);
    }

    this.username = input.readUTF();
    this.readContents(input);
  }

  public abstract void writeContents(ByteArrayDataOutput output);

  public abstract void readContents(ByteArrayDataInput input);
}
