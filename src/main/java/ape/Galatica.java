package ape;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Galatica {
  public static void main(String[] args) throws Exception {
    String secretKeyLocation = System.getenv("HOME") + "/.openai";

    // TODO: provide an over-ride argument to have a different location
    String secretFile = Files.readString(Path.of(secretKeyLocation));
    ObjectMapper M = new com.fasterxml.jackson.databind.ObjectMapper();
    String secretKey = M.readTree(secretFile).get("key").textValue();

    String preamble = "";
    File preambleFile = new File("preamble.txt");
    if (preambleFile.exists()) {
      preamble = Files.readString(preambleFile.toPath()) + " ";
    }

    String prompt = preamble + String.join(" ", args);

    System.out.println(gen(secretKey, prompt));
  }

  private static String gen(String secretKey, String prompt) throws IOException {
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
