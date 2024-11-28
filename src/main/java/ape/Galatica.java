package ape;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class Galatica {

  private static String extOf(String filename) {
    int kLastDot = filename.lastIndexOf('.');
    if (kLastDot >= 0) {
      return filename.substring(kLastDot + 1);
    }
    return null;
  }

  private static String selectCode(String ext, String output) {
    if (ext != null) {
      int first = output.indexOf("```" + ext);
      if (first > 0) {
        int end = output.lastIndexOf("```", first + 1);
        if (first < end) {
          return output.substring(first + 3 + ext.length(), end);
        }
      }
    }
    return output;
  }

  public static void main(String[] args) throws Exception {
    ObjectMapper M = new com.fasterxml.jackson.databind.ObjectMapper();

    String secretConfigLocation = System.getenv("HOME") + "/.ai";

    // TODO: provide an over-ride argument to have a different location
    String secretConfigBody = Files.readString(Path.of(secretConfigLocation));
    ObjectNode config = (ObjectNode) M.readTree(secretConfigBody);

    String secretKey = config.get("key").textValue();
    String vendor = config.get("vendor").textValue();


    StringBuilder preamble = new StringBuilder();
    File preambleFile = new File("preamble.txt");
    if (preambleFile.exists()) {
      preamble.append(Files.readString(preambleFile.toPath()).trim()).append("\n");
    }

    File databasePackageFile = new File("db.json");
    if (databasePackageFile.exists()) {
      String databaseInfo = Files.readString(databasePackageFile.toPath());
      DataBase db = new DataBase(M.readTree(databaseInfo));
      for (String table : db.showTables()) {
        preamble.append(db.toPrompt(table));
      }
    }

    boolean isUpdate = "--update".equalsIgnoreCase(args[0]);
    boolean isCreate = "--create".equalsIgnoreCase(args[0]);
    boolean isAppend = "--append".equalsIgnoreCase(args[0]);
    File writeDestination = null;
    int startAt = 0;
    String ext = null;
    if (isUpdate) {
      startAt = 2;
      writeDestination = new File(args[1]);
      preamble.append("The file to update is:\n");
      preamble.append("```").append(extOf(args[1])).append("\n");
      preamble.append(Files.readString(writeDestination.toPath()));
      preamble.append("```\n");
      ext = extOf(args[1]);
    } else if (isCreate || isAppend) {
      startAt = 2;
      writeDestination = new File(args[1]);
      ext = extOf(args[1]);
    }

    ArrayList<String> promptList = new ArrayList<>();
    for (int k = startAt; k < args.length; k++) {
      promptList.add(args[k]);
    }

    String prompt = String.join(" ", promptList);

    String output = null;
    if ("openai".equals(vendor)) {
      if (config.has("model") && "turbo35".equals(config.get("model").textValue())) {
        output = turbo35(secretKey, preamble.toString() + prompt);
      } else {
        output = gpt4o(secretKey, preamble.toString(), prompt);
      }
    } else if ("anthropic".equals(vendor)) {
      output = claude(secretKey, preamble.toString(), prompt);
    } else {
      throw new Exception("Unknown vendor: " + vendor);
    }

    output = selectCode(ext, output);

    if (isUpdate || isCreate) {
      Files.writeString(writeDestination.toPath(), output);
    } else if (isAppend) {
      Files.writeString(writeDestination.toPath(), Files.readString(writeDestination.toPath()) + "\n" + output);
    } else {
      System.out.println(output);
    }
  }

  private static String claude(String secretKey, String preamble, String prompt) throws IOException {
    OkHttpClient client = new OkHttpClient.Builder() //
        .connectTimeout(10, TimeUnit.SECONDS) //
        .writeTimeout(10, TimeUnit.SECONDS) //
        .readTimeout(120, TimeUnit.SECONDS) //
        .build();

    // JSON body to send with the request
    ObjectNode node = new JsonMapper().createObjectNode();
    node.put("model", "claude-3-5-sonnet-20241022");
    node.put("max_tokens", 2048);
    node.put("system", preamble);

    ArrayNode messages = node.putArray("messages");

    ObjectNode ask = messages.addObject();
    ask.put("role", "user");
    ask.put("content", prompt);

    RequestBody body = RequestBody.create(node.toString(), MediaType.get("application/json; charset=utf-8"));

    Request request = new Request.Builder()
        .url("https://api.anthropic.com/v1/messages") //
        .header("x-api-key", secretKey) //
        .header("anthropic-version", "2023-06-01") //
        .post(body)
        .build();
    // Execute the request
    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Unexpected code " + response + "//" + response.body().string());
      }

      // Parse the response body
      String responseBody = response.body().string();
      return new JsonMapper().readTree(responseBody).get("content").get(0).get("text").textValue().trim();
    }
  }

  private static String gpt4o(String secretKey, String preamble, String prompt) throws IOException {
    OkHttpClient client = new OkHttpClient.Builder() //
        .connectTimeout(10, TimeUnit.SECONDS) //
        .writeTimeout(10, TimeUnit.SECONDS) //
        .readTimeout(120, TimeUnit.SECONDS) //
        .build();

    // JSON body to send with the request
    ObjectNode node = new JsonMapper().createObjectNode();

    node.put("model", "gpt-4o");  // You can use different models like "gpt-3.5-turbo"
    ArrayNode messages = node.putArray("messages");

    ObjectNode setup = messages.addObject();
    setup.put("role", "system");
    setup.put("content", preamble);

    ObjectNode promptNode = messages.addObject();
    promptNode.put("role", "user");
    promptNode.put("content", prompt);

    RequestBody body = RequestBody.create(node.toString(), MediaType.get("application/json; charset=utf-8"));

    // Create a request to OpenAI API
    Request request = new Request.Builder()
        .url("https://api.openai.com/v1/chat/completions")
        .header("Authorization", "Bearer " + secretKey)
        .post(body)
        .build();

    // Execute the request
    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Unexpected code " + response + "//" + response.body().string());
      }

      // Parse the response body
      String responseBody = response.body().string();
      return new JsonMapper().readTree(responseBody).get("choices").get(0).get("message").get("content").textValue().trim();
    }
  }

  private static String turbo35(String secretKey, String prompt) throws IOException {
    OkHttpClient client = new OkHttpClient();
    // JSON body to send with the request
    ObjectNode node = new JsonMapper().createObjectNode();

    node.put("model", "gpt-3.5-turbo-instruct");  // You can use different models like "gpt-3.5-turbo"
    node.put("prompt", prompt);
    node.put("max_tokens", 2048);  // Adjust as needed
    node.put("temperature", 0.7);  // Adjust for creativity

    RequestBody body = RequestBody.create(node.toString(), MediaType.get("application/json; charset=utf-8"));

    // Create a request to OpenAI API
    Request request = new Request.Builder()
        .url("https://api.openai.com/v1/completions")
        .header("Authorization", "Bearer " + secretKey)
        .post(body)
        .build();

    // Execute the request
    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Unexpected code " + response + "//" + response.body().string());
      }

      // Parse the response body
      String responseBody = response.body().string();

      return new JsonMapper().readTree(responseBody).get("choices").get(0).get("text").textValue().trim();
    }
  }
}
