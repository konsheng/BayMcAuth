package com.baymc.auth.backend.type;

import com.baymc.auth.BayMcAuth;
import com.baymc.auth.backend.Endpoint;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import java.util.function.Function;

public class LongEndpoint extends Endpoint {

  private Function<String, Long> function;
  private long value;

  public LongEndpoint(BayMcAuth plugin, String type, Function<String, Long> function) {
    super(plugin, type, null);
    this.function = function;
  }

  public LongEndpoint(BayMcAuth plugin, String type, String username, long value) {
    super(plugin, type, username);
    this.value = value;
  }

  @Override
  public void writeContents(ByteArrayDataOutput output) {
    output.writeLong(this.value);
  }

  @Override
  public void readContents(ByteArrayDataInput input) {
    this.value = this.function.apply(this.username);
  }

  @Override
  public String toString() {
    return "LongEndpoint{"
        + "username='" + this.username + '\''
        + ", value=" + this.value
        + '}';
  }
}
