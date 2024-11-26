package ape;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class Galatica {
  public static void main(String[] args) throws Exception {
    ObjectMapper M = new com.fasterxml.jackson.databind.ObjectMapper();

    String secretKeyLocation = System.getenv("HOME") + "/.openai";

    // TODO: provide an over-ride argument to have a different location
    String secretFile = Files.readString(Path.of(secretKeyLocation));
    String secretKey = M.readTree(secretFile).get("key").textValue();

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

    System.out.println(gpt4o(secretKey, preamble.toString(), String.join(" ", args)));
    // TODO: make this an optional in the config
    // System.out.println(turbo35(secretKey, preamble.toString() + String.join(" ", args)));
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
