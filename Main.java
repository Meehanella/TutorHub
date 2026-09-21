import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

class User {
    protected String username;
    protected String password;
    protected String role;
    protected String name;
    protected String email;
    protected String phone;

    public User(String username, String password, String role, String name, String email, String phone) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public boolean authenticate(String u, String p) {
        return this.username.equals(u) && this.password.equals(p);
    }

    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getName() { return name; }

    public String toJson() {
        return String.format("{\"username\":\"%s\",\"role\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\"}", 
                username, role, name, email, phone);
    }
}

class TutorUser extends User {
    private String subject;
    private double rate;
    private boolean isAvailable;
    private String bookedByName = "";
    private String bookedByEmail = "";
    private String bookedByPhone = "";

    public TutorUser(String username, String password, String name, String email, String phone, String subject, double rate, boolean isAvailable) {
        super(username, password, "TUTOR", name, email, phone);
        this.subject = subject;
        this.rate = rate;
        this.isAvailable = isAvailable;
    }

    public void setSubject(String subject) { this.subject = subject; }
    public void setRate(double rate) { this.rate = rate; }
    public void setAvailable(boolean available) { 
        this.isAvailable = available; 
        if (available) {
            this.bookedByName = "";
            this.bookedByEmail = "";
            this.bookedByPhone = "";
        }
    }

    public void setBooking(User student) {
        this.isAvailable = false;
        this.bookedByName = student.name;
        this.bookedByEmail = student.email;
        this.bookedByPhone = student.phone;
    }

    @Override
    public String toJson() {
        return String.format("{\"username\":\"%s\",\"role\":\"TUTOR\",\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"subject\":\"%s\",\"rate\":%.2f,\"available\":%b,\"studentName\":\"%s\",\"studentEmail\":\"%s\",\"studentPhone\":\"%s\"}",
                username, name, email, phone, subject, rate, isAvailable, bookedByName, bookedByEmail, bookedByPhone);
    }
}

class StudentUser extends User {
    private String gradeLevel;

    public StudentUser(String username, String password, String name, String email, String phone, String gradeLevel) {
        super(username, password, "STUDENT", name, email, phone);
        this.gradeLevel = gradeLevel;
    }

    @Override
    public String toJson() {
        return String.format("{\"username\":\"%s\",\"role\":\"STUDENT\",\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"grade\":\"%s\"}",
                username, name, email, phone, gradeLevel);
    }
}

public class Main {
    private static ArrayList<User> userList = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        userList.add(new User("admin", "1234", "ADMIN", "System Admin", "admin@tutor.edu", "0917-000-0000"));
        userList.add(new TutorUser("tutor1", "1234", "Maria Santos", "maria@tutor.edu", "0917-111-2222", "Java Programming", 250.0, true));
        userList.add(new TutorUser("tutor2", "1234", "Juan Dela Cruz", "juan@tutor.edu", "0918-333-4444", "Web Development", 300.0, false));
        userList.add(new StudentUser("student1", "1234", "Alex Reyes", "alex@student.edu", "0919-555-6666", "2nd Year IT"));

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", exchange -> {
            byte[] response = Files.readAllBytes(Paths.get("index.html"));
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        });

        server.createContext("/api/login", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes());
                String user = parseVal(body, "username");
                String pass = parseVal(body, "password");

                User matchedUser = null;
                for (User u : userList) {
                    if (u.authenticate(user, pass)) {
                        matchedUser = u;
                        break;
                    }
                }

                String jsonResponse = (matchedUser != null)
                    ? String.format("{\"status\":\"success\",\"user\":%s}", matchedUser.toJson())
                    : "{\"status\":\"fail\"}";

                sendJsonResponse(exchange, jsonResponse);
            }
        });

        server.createContext("/api/register", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes());
                String role = parseVal(body, "role");
                String u = parseVal(body, "username");
                String p = parseVal(body, "password");
                String n = parseVal(body, "name");
                String email = parseVal(body, "email");
                String phone = parseVal(body, "phone");

                for (User user : userList) {
                    if (user.getUsername().equals(u)) {
                        sendJsonResponse(exchange, "{\"status\":\"exists\"}");
                        return;
                    }
                }

                if ("TUTOR".equals(role)) {
                    String sub = parseVal(body, "subject");
                    String rateStr = parseVal(body, "rate");
                    double rate = rateStr.isEmpty() ? 0.0 : Double.parseDouble(rateStr);
                    userList.add(new TutorUser(u, p, n, email, phone, sub.isEmpty() ? "General" : sub, rate, true));
                } else {
                    String grade = parseVal(body, "grade");
                    userList.add(new StudentUser(u, p, n, email, phone, grade.isEmpty() ? "1st Year" : grade));
                }

                sendJsonResponse(exchange, "{\"status\":\"success\"}");
            }
        });

        server.createContext("/api/users", exchange -> {
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < userList.size(); i++) {
                    json.append(userList.get(i).toJson());
                    if (i < userList.size() - 1) json.append(",");
                }
                json.append("]");
                sendJsonResponse(exchange, json.toString());
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = new String(exchange.getRequestBody().readAllBytes());
                String action = parseVal(body, "action");

                if ("delete".equals(action)) {
                    String targetUser = parseVal(body, "username");
                    userList.removeIf(u -> u.getUsername().equals(targetUser));
                    sendJsonResponse(exchange, "{\"status\":\"success\"}");
                }
            }
        });

        server.createContext("/api/tutor/action", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes());
                String action = parseVal(body, "action");
                String tutorUsername = parseVal(body, "username");

                for (User u : userList) {
                    if (u instanceof TutorUser && u.getUsername().equals(tutorUsername)) {
                        TutorUser t = (TutorUser) u;

                        if ("book".equals(action)) {
                            String studentUsername = parseVal(body, "studentUsername");
                            for (User s : userList) {
                                if (s.getUsername().equals(studentUsername)) {
                                    t.setBooking(s);
                                    break;
                                }
                            }
                        } else if ("update".equals(action)) {
                            t.setSubject(parseVal(body, "subject"));
                            t.setRate(Double.parseDouble(parseVal(body, "rate")));
                            boolean avail = Boolean.parseBoolean(parseVal(body, "available"));
                            t.setAvailable(avail);
                        }
                        break;
                    }
                }
                sendJsonResponse(exchange, "{\"status\":\"success\"}");
            }
        });

        server.start();
        System.out.println("Server running at http://localhost:8080");
    }

    private static void sendJsonResponse(HttpExchange exchange, String json) throws IOException {
        byte[] response = json.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length);
        OutputStream os = exchange.getResponseBody();
        os.write(response);
        os.close();
    }

    private static String parseVal(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k == -1) return "";
        int vStart = json.indexOf(":", k) + 1;
        int vEnd = json.indexOf(",", vStart);
        if (vEnd == -1) vEnd = json.indexOf("}", vStart);
        if (vEnd == -1) vEnd = json.length();
        
        return json.substring(vStart, vEnd)
                   .replace("\"", "")
                   .replace("}", "")
                   .trim();
    }
}
