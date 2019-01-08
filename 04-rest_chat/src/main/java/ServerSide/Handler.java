package ServerSide;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;

class Handler implements HttpHandler {
    private Headers headers;
    private Headers respHeaders;
    private URI uri;
    private StringBuilder body;

    private JSONParser jsonParser;
    private JSONObject inputJson = null;
    private JSONObject outputJson = null;

    private final static int EXCEPTION = 500;
    private final static int INVALID_METHOD = 405;
    private final static int NO_USER = 404;
    private final static int INVALID_TOKEN = 403;
    private final static int NO_TOKEN = 401;
    private final static int INVALID_FORMAT = 400;

    private final static int OK = 200;
    private int code = OK;

    private void parseInput() {
        try {
            inputJson = (JSONObject) jsonParser.parse(body.toString());
        }
        catch (ParseException e) {
            code = EXCEPTION;
            e.printStackTrace();
        }
    }
    private User findUser(int id) {
        for (User u : Server.usersList) {
            if(u.getId() == id)
                return u;
        }
        return null;
    }
    private User checkToken() {
        String id = headers.get("Authorization").get(0);
        if(id == null) {
            code = NO_TOKEN;
            return null;
        }
        synchronized (Server.usersList) {
            for (User u : Server.usersList) {
                if (u.getToken().equals(id))
                    return u;
            }
        }
        code = INVALID_TOKEN;
        return null;
    }

    private void handleLogin() {
        parseInput();
        String name = (String)inputJson.get("username");

        synchronized (Server.usersList) {
            for (User buf : Server.usersList) {
                if (buf.getName().equals(name)) {
                    code = NO_TOKEN;
                    respHeaders.add("WWW-Authenticate", "Token realm='Username is already in use'");
                    return;
                }
            }
            User u = new User(name);
            Server.usersList.add(u);

            code = OK;
            respHeaders.add("Content-Type", "application/json");
            outputJson.put("id", u.getId());
            outputJson.put("username", name);
            outputJson.put("online", true);
            outputJson.put("token", u.getToken());
        }
    }

    private void handleLogout() {
        User u = checkToken();
        if(u == null)
            return;
        synchronized (Server.usersList) {
            Server.usersList.remove(u);
        }
        code = OK;
        respHeaders.add("Content-Type", "application/json");
        outputJson.put("message", "bye!");
    }

    private void handleUser() {
        if(checkToken() == null)
            return;
        String[] buf = uri.getPath().split("/");
        synchronized (Server.usersList) {
            if (buf.length == 2) {
                ArrayList<JSONObject> users = new ArrayList<>();
                for (User u : Server.usersList) {
                    JSONObject userInfo = new JSONObject();
                    userInfo.put("id", u.getId());
                    userInfo.put("username", u.getName());
                    userInfo.put("online", u.getStatus());
                    users.add(userInfo);
                }
                JSONArray usersJson = new JSONArray();
                usersJson.addAll(users);
                outputJson.put("users", usersJson);
            }

            else if (buf.length == 3) {
                int id = Integer.parseInt(buf[2]);
                User target = findUser(id);
                if (target == null) {
                    code = NO_USER;
                }
                else {
                    outputJson.put("id", id);
                    outputJson.put("username", target.getName());
                    outputJson.put("online", target.getStatus());
                }
            }
        }
        code = OK;
        respHeaders.add("Content-Type", "application/json");
    }

    private void handleMessagesList() {
        User u = checkToken();
        if(u == null)
            return;

        String[] buf = uri.getQuery().split("[?|&]");
        if(buf.length != 2) {
            code = INVALID_FORMAT;
            return;
        }
        int index = buf[0].indexOf("offset=");
        int offset = 0, count = 10;
        if(index != -1) {
            offset = Integer.parseInt(buf[0].substring(7));
        }
        index = buf[1].indexOf("count=");
        if(index != -1) {
            count = Integer.parseInt(buf[1].substring(6));
        }

        ArrayList<JSONObject> messages = new ArrayList<>();

        int endIndex =  (offset + count) > Server.mesHistory.size() ? Server.mesHistory.size() : (offset + count);
        ArrayList<String> subMesHistory = new ArrayList<>(Server.mesHistory.subList(offset, endIndex));
        int id = offset;
        for (String m : subMesHistory) {
            JSONObject mesInfo = new JSONObject();
            String[] tokens = m.split(":",  2);
            mesInfo.put("id", ++id);
            mesInfo.put("message", tokens[1]);
            mesInfo.put("author", tokens[0]);
            messages.add(mesInfo);
        }
        JSONArray messagesJson = new JSONArray();
        messagesJson.addAll(messages);
        code = OK;
        respHeaders.add("Content-Type", "application/json");
        outputJson.put("messages", messagesJson);
    }

    private void handleMessage() {
        User u = checkToken();
        if(u == null)
            return;

        parseInput();
        u.setStatus(true);
        String message = (String)inputJson.get("message");
        respHeaders.add("Content-Type", "application/json");
        outputJson.put("id", u.getId());
        outputJson.put("message", message);
        message = u.getName() + ":" + message;
        code = OK;
        Server.mesHistory.add(0, message);
    }

    private void init(HttpExchange exchange) {
        uri = exchange.getRequestURI();
        headers = exchange.getRequestHeaders();
        body = new StringBuilder();
        jsonParser = new JSONParser();
        outputJson = new JSONObject();
    }

    @Override
    public void handle(HttpExchange exchange) {
        init(exchange);
        String method = exchange.getRequestMethod();
        try {
            try (InputStreamReader is = new InputStreamReader(exchange.getRequestBody())) {
                char[] buf = new char[256];
                int read;
                while ((read = is.read(buf)) != -1) {
                    body.append(buf, 0, read);
                }
            }
            respHeaders = exchange.getResponseHeaders();
            String path = uri.getPath();
            switch (method) {
                case ("GET"):
                    switch (path) {
                        case ("/users"):
                            handleUser();
                            break;
                        case ("/messages"):
                            handleMessagesList();
                            break;
                        default:
                            code = INVALID_FORMAT;
                            break;
                    }
                    break;
                case ("POST"):
                    switch (path) {
                        case ("/login"):
                            handleLogin();
                            break;
                        case ("/logout"):
                            handleLogout();
                            break;
                        case ("/messages"):
                            handleMessage();
                            break;
                        default:
                            code = INVALID_FORMAT;
                            break;
                    }
                    break;
                default:
                    code = INVALID_METHOD;
            }
        }
        catch (IOException e) {
            code = EXCEPTION;
            e.printStackTrace();
        }
        finally {
            try (OutputStream os = exchange.getResponseBody()) {
                String response = outputJson.toString();
                exchange.sendResponseHeaders(code, response.length());
                os.write(response.getBytes());
                os.flush();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}