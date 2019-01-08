package ClientSide;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.UUID;


class ClientConnection {
    private Client client;
    private URL url;
    private HttpURLConnection connection;

    private boolean isLogout = false;
    private boolean hasBody = false;

    private JSONObject body;
    private JSONParser jsonParser = new JSONParser();

    private String method;

    ClientConnection(Client client, URL url, HttpURLConnection connection) {
        this.client = client;
        this.url = url;
        this.connection = connection;
    }

    void handle() {
        String path = url.getPath();
        String query = url.getQuery();
        switch(path) {
            case "/login":
                method = "POST";
                setLoginBody();
                break;
            case "/logout":
                method = "POST";
                isLogout = true;
                break;
            case "/messages":
                if(query == null) {
                    method = "POST";
                    setMessageBody();
                }
                else
                    method = "GET";
                break;
            default:
                method = "GET";
        }
        sendRequest();
        getResponse();
    }


    private void setLoginBody() {
        hasBody = true;
        body = new JSONObject();
        body.put("username", client.getRawName());
    }

    private void setMessageBody() {
        hasBody = true;
        String message = client.getMessage();
        body = new JSONObject();
        body.put("message", message);
    }

    private void sendRequest() {
        try {
            connection.setRequestMethod(method);
        }
        catch(ProtocolException e) {
            e.printStackTrace();
        }
        connection.setRequestProperty("Content-Type", "application/json");
        if(client.getToken() != null)
            connection.setRequestProperty("Authorization", client.getToken().toString());
        connection.setDoOutput(true);
        if(hasBody) {
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.toJSONString().getBytes());
                os.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void getResponse() {
        try {
            int code = connection.getResponseCode();
            if(code != 200) {
                handleError(code);
                return;
            }
        }
        catch(IOException e) {
            e.printStackTrace();
        }

        try (InputStreamReader is = new InputStreamReader(connection.getInputStream())) {
            char[] buf = new char[256];
            StringBuilder body = new StringBuilder();
            int read;
            while ((read = is.read(buf)) != -1) {
                body.append(buf, 0, read);
            }

            JSONObject response = (JSONObject) jsonParser.parse(body.toString());
            client.printResponse(response.toString());
            if(client.getToken() == null) {
                String token = (String)response.get("token");
                client.login(UUID.fromString(token));
            }
            else if(isLogout) {
                client.logout();
            }
        }
        catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    private void handleError(int code) throws IOException {
        String responseMessage = connection.getResponseMessage();
        client.printErrorMess(responseMessage);
        if(code == 401) {
            responseMessage = connection.getHeaderField("WWW-Authenticate");
            client.printErrorMess(responseMessage);
            client.errorAuthorization();
        }
    }
}
