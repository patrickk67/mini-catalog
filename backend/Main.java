import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * Mini e-commerce demo server. Uses only the Java standard library.
 * Serves frontend files and a small JSON REST API from port 8080.
 */
public class Main {
    static final int PORT = 8080;
    static final Path ROOT = Paths.get("..").toAbsolutePath().normalize();
    static final Path WEB = ROOT.resolve("frontend").normalize();
    static final Path DATA_DIR = Paths.get("data");
    static final Path DATA_FILE = DATA_DIR.resolve("products.json");
    static final ProductStore STORE = new ProductStore(DATA_FILE);

    public static void main(String[] args) throws Exception {
        STORE.load();
        int port = args.length > 0 ? Integer.parseInt(args[0]) : PORT;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/api/", Main::handleApi);
        server.createContext("/", Main::handleStatic);
        server.start();
        System.out.println("Mini E-Commerce is running at http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop.");
    }

    static void handleApi(HttpExchange ex) throws IOException {
        addHeaders(ex);
        String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);
        String path = ex.getRequestURI().getPath();
        if ("OPTIONS".equals(method)) { send(ex, 204, ""); return; }

        try {
            if ("/api/products".equals(path)) {
                if ("GET".equals(method)) {
                    sendJson(ex, 200, Json.stringify(STORE.all()));
                } else if ("POST".equals(method)) {
                    Map<String,Object> body = readBodyObject(ex);
                    Product p = Product.from(body, STORE.nextId());
                    STORE.add(p);
                    sendJson(ex, 201, Json.stringify(p.toMap()));
                } else {
                    sendError(ex, 405, "Method not allowed");
                }
                return;
            }
            if ("/api/categories".equals(path) && "GET".equals(method)) {
                Set<String> categories = STORE.categories();
                sendJson(ex, 200, Json.stringify(categories));
                return;
            }
            if (path.startsWith("/api/products/")) {
                String rawId = path.substring("/api/products/".length());
                int id;
                try { id = Integer.parseInt(rawId); }
                catch (NumberFormatException e) { sendError(ex, 400, "Product ID must be a number"); return; }

                Product existing = STORE.find(id);
                if (existing == null) { sendError(ex, 404, "Product not found"); return; }

                if ("GET".equals(method)) {
                    sendJson(ex, 200, Json.stringify(existing.toMap()));
                } else if ("PUT".equals(method)) {
                    Product updated = Product.from(readBodyObject(ex), id);
                    STORE.update(id, updated);
                    sendJson(ex, 200, Json.stringify(updated.toMap()));
                } else if ("DELETE".equals(method)) {
                    STORE.delete(id);
                    send(ex, 204, "");
                } else {
                    sendError(ex, 405, "Method not allowed");
                }
                return;
            }
            sendError(ex, 404, "API route not found");
        } catch (IllegalArgumentException e) {
            sendError(ex, 400, e.getMessage() == null ? "Invalid input" : e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "Server error. Check the Java terminal for details.");
        } finally {
            ex.close();
        }
    }

    static void handleStatic(HttpExchange ex) throws IOException {
        String path = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
        if (path.equals("/")) path = "/index.html";
        Path target = WEB.resolve(path.substring(1)).normalize();
        if (!target.startsWith(WEB) || !Files.exists(target) || Files.isDirectory(target)) {
            byte[] bytes = "Not found".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(404, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
            return;
        }
        String type = "text/plain; charset=utf-8";
        String name = target.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) type = "text/html; charset=utf-8";
        else if (name.endsWith(".css")) type = "text/css; charset=utf-8";
        else if (name.endsWith(".js")) type = "application/javascript; charset=utf-8";
        else if (name.endsWith(".svg")) type = "image/svg+xml";
        byte[] bytes = Files.readAllBytes(target);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    static void addHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }
    static Map<String,Object> readBodyObject(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Object value = Json.parse(body);
        if (!(value instanceof Map)) throw new IllegalArgumentException("Request body must be a JSON object");
        @SuppressWarnings("unchecked") Map<String,Object> map = (Map<String,Object>) value;
        return map;
    }
    static void sendJson(HttpExchange ex, int status, String body) throws IOException { send(ex, status, body); }
    static void sendError(HttpExchange ex, int status, String message) throws IOException {
        sendJson(ex, status, Json.stringify(Map.of("error", message)));
    }
    static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (status == 204) { ex.sendResponseHeaders(status, -1); return; }
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
    }
}

class Product {
    int id;
    String name, description, category, image;
    double price;
    int stock;
    double rating;

    Product(int id, String name, String description, double price, String category, String image, int stock, double rating) {
        this.id=id; this.name=name; this.description=description; this.price=price;
        this.category=category; this.image=image; this.stock=stock; this.rating=rating;
    }

    static Product from(Map<String,Object> m, int id) {
        String name = str(m, "name", "");
        String description = str(m, "description", "");
        String category = str(m, "category", "Other");
        String image = str(m, "image", "");
        double price = num(m, "price", -1);
        int stock = (int)num(m, "stock", -1);
        double rating = num(m, "rating", 4.5);
        if (name.isBlank()) throw new IllegalArgumentException("Product name is required");
        if (description.isBlank()) throw new IllegalArgumentException("Description is required");
        if (category.isBlank()) throw new IllegalArgumentException("Category is required");
        if (price < 0 || !Double.isFinite(price)) throw new IllegalArgumentException("Price must be zero or more");
        if (stock < 0 || stock > 100000) throw new IllegalArgumentException("Stock must be between 0 and 100000");
        if (rating < 0 || rating > 5) throw new IllegalArgumentException("Rating must be between 0 and 5");
        if (image.isBlank()) image = "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=800&q=80";
        return new Product(id, name.trim(), description.trim(), price, category.trim(), image.trim(), stock, rating);
    }
    static String str(Map<String,Object> m, String key, String def) {
        Object v=m.get(key); return v == null ? def : String.valueOf(v);
    }
    static double num(Map<String,Object> m, String key, double def) {
        Object v=m.get(key);
        if (v instanceof Number) return ((Number)v).doubleValue();
        if (v == null) return def;
        try { return Double.parseDouble(String.valueOf(v)); }
        catch(Exception e) { return def; }
    }
    Map<String,Object> toMap() {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id",id); m.put("name",name); m.put("description",description); m.put("price",price);
        m.put("category",category); m.put("image",image); m.put("stock",stock); m.put("rating",rating);
        return m;
    }
}

class ProductStore {
    private final Path file;
    private final List<Product> products = new ArrayList<>();
    private int nextId = 1;
    ProductStore(Path file) { this.file=file; }

    synchronized void load() throws IOException {
        Files.createDirectories(file.getParent());
        if (Files.exists(file) && Files.size(file) > 0) {
            Object root = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            if (root instanceof List) {
                products.clear();
                for (Object o : (List<?>)root) {
                    if (o instanceof Map) {
                        @SuppressWarnings("unchecked") Map<String,Object> m=(Map<String,Object>)o;
                        Product p=Product.from(m, (int)Product.num(m,"id",nextId));
                        products.add(p); nextId=Math.max(nextId,p.id+1);
                    }
                }
            }
        } else {
            products.addAll(seed());
            save();
        }
        for (Product p: products) nextId=Math.max(nextId,p.id+1);
    }
    synchronized List<Map<String,Object>> all() {
        List<Map<String,Object>> out=new ArrayList<>();
        for(Product p:products) out.add(p.toMap());
        return out;
    }
    synchronized Set<String> categories() {
        Set<String> out = new TreeSet<>();
        for (Product p : products) out.add(p.category);
        return out;
    }
    synchronized Product find(int id) { for(Product p:products) if(p.id==id) return p; return null; }
    synchronized int nextId() { return nextId; }
    synchronized void add(Product p) throws IOException { p.id=nextId++; products.add(p); save(); }
    synchronized void update(int id, Product p) throws IOException {
        for(int i=0;i<products.size();i++) if(products.get(i).id==id) { p.id=id; products.set(i,p); save(); return; }
        throw new IllegalArgumentException("Product not found");
    }
    synchronized void delete(int id) throws IOException { products.removeIf(p->p.id==id); save(); }
    private void save() throws IOException {
        List<Map<String,Object>> out=new ArrayList<>();
        for(Product p:products) out.add(p.toMap());
        Files.writeString(file, Json.stringify(out), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    private List<Product> seed() {
        List<Product> p=new ArrayList<>();
        p.add(new Product(1,"Everyday Smartwatch","A sleek smartwatch for everyday activity tracking and notifications.",2499,"Electronics","https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=900&q=85",18,4.6));
        p.add(new Product(2,"Wireless Headphones","Comfortable over-ear headphones with rich sound.",1899,"Electronics","https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=85",12,4.5));
        p.add(new Product(3,"Minimal Sneakers","Lightweight everyday sneakers with a clean silhouette.",2199,"Fashion","https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=85",20,4.4));
        p.add(new Product(4,"Classic Backpack","A roomy, versatile backpack for college and travel.",1299,"Accessories","https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=900&q=85",15,4.7));
        p.add(new Product(5,"Ceramic Coffee Set","A simple ceramic mug set for your slow mornings.",799,"Home & Living","https://images.unsplash.com/photo-1514228742587-6b1558fcca3d?auto=format&fit=crop&w=900&q=85",9,4.3));
        p.add(new Product(6,"Desk Lamp","A modern desk lamp for focused study sessions.",999,"Home & Living","https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=900&q=85",11,4.4));
        p.add(new Product(7,"Everyday Sunglasses","UV-style inspired sunglasses with a timeless frame.",649,"Fashion","https://images.unsplash.com/photo-1511499767150-a48a237f0083?auto=format&fit=crop&w=900&q=85",25,4.2));
        p.add(new Product(8,"Mechanical Keyboard","A compact keyboard for work, study, and play.",2799,"Electronics","https://images.unsplash.com/photo-1589578228447-e1a4e481c6c3?auto=format&fit=crop&w=900&q=85",7,4.8));
        p.add(new Product(9,"Daily Journal","A premium notebook to capture plans and ideas.",349,"Books","https://images.unsplash.com/photo-1531346878377-a5be20888e57?auto=format&fit=crop&w=900&q=85",30,4.5));
        p.add(new Product(10,"Travel Water Bottle","Reusable bottle designed for busy days on the go.",499,"Accessories","https://images.unsplash.com/photo-1602143407151-7111542de6e8?auto=format&fit=crop&w=900&q=85",22,4.3));
        p.add(new Product(11,"Soft Throw Blanket","A cozy layer for your sofa or reading corner.",1199,"Home & Living","https://images.unsplash.com/photo-1600369671236-e74521d4b6ad?auto=format&fit=crop&w=900&q=85",8,4.6));
        p.add(new Product(12,"Classic Novel","A timeless fiction pick for your bookshelf.",399,"Books","https://images.unsplash.com/photo-1544947950-fa07a98d237f?auto=format&fit=crop&w=900&q=85",14,4.4));
        return p;
    }
}

/** Small JSON parser/stringifier so the project needs no third-party libraries. */
class Json {
    static Object parse(String s) {
        Parser p=new Parser(s);
        Object value=p.value();
        p.ws();
        if(p.i!=s.length()) throw new IllegalArgumentException("Invalid JSON near character " + p.i);
        return value;
    }
    static String stringify(Object o) {
        if(o==null) return "null";
        if(o instanceof String) return quote((String)o);
        if(o instanceof Number || o instanceof Boolean) return String.valueOf(o);
        if(o instanceof Map) {
            StringBuilder b=new StringBuilder("{"); boolean first=true;
            for(Object entryObj:((Map<?,?>)o).entrySet()) {
                Map.Entry<?,?> e=(Map.Entry<?,?>)entryObj;
                if(!first)b.append(','); first=false;
                b.append(quote(String.valueOf(e.getKey()))).append(':').append(stringify(e.getValue()));
            }
            return b.append('}').toString();
        }
        if(o instanceof Iterable) {
            StringBuilder b=new StringBuilder("["); boolean first=true;
            for(Object v:(Iterable<?>)o){if(!first)b.append(',');first=false;b.append(stringify(v));}
            return b.append(']').toString();
        }
        return quote(String.valueOf(o));
    }
    static String quote(String s) {
        StringBuilder b=new StringBuilder("\"");
        for(char c:s.toCharArray()) {
            switch(c) {
                case '"': b.append("\\\""); break; case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break; case '\r': b.append("\\r"); break; case '\t': b.append("\\t"); break;
                default: if(c<32)b.append(String.format("\\u%04x",(int)c)); else b.append(c);
            }
        }
        return b.append('"').toString();
    }
    static class Parser {
        final String s; int i=0;
        Parser(String s){this.s=s==null?"":s;}
        void ws(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}
        Object value(){
            ws(); if(i>=s.length()) throw new IllegalArgumentException("Empty JSON body");
            char c=s.charAt(i);
            if(c=='{')return object(); if(c=='[')return array(); if(c=='"')return string();
            if(c=='t'&&take("true"))return true; if(c=='f'&&take("false"))return false; if(c=='n'&&take("null"))return null;
            return number();
        }
        boolean take(String x){if(s.startsWith(x,i)){i+=x.length();return true;}return false;}
        Map<String,Object> object(){
            Map<String,Object> m=new LinkedHashMap<>(); i++; ws(); if(i<s.length()&&s.charAt(i)=='}'){i++;return m;}
            while(true){ws(); if(i>=s.length()||s.charAt(i)!='"')throw new IllegalArgumentException("Expected JSON object key");
                String k=string(); ws(); if(i>=s.length()||s.charAt(i++)!=':')throw new IllegalArgumentException("Expected ':'");
                m.put(k,value()); ws(); if(i<s.length()&&s.charAt(i)=='}'){i++;return m;}
                if(i>=s.length()||s.charAt(i++)!=',')throw new IllegalArgumentException("Expected ','");
            }
        }
        List<Object> array(){
            List<Object> a=new ArrayList<>(); i++; ws(); if(i<s.length()&&s.charAt(i)==']'){i++;return a;}
            while(true){a.add(value());ws();if(i<s.length()&&s.charAt(i)==']'){i++;return a;}
                if(i>=s.length()||s.charAt(i++)!=',')throw new IllegalArgumentException("Expected ','");}
        }
        String string(){
            if(s.charAt(i++)!='"')throw new IllegalArgumentException("Expected string");
            StringBuilder b=new StringBuilder();
            while(i<s.length()){char c=s.charAt(i++);if(c=='"')return b.toString();
                if(c=='\\'){if(i>=s.length())break;char e=s.charAt(i++);
                    switch(e){case '"':b.append('"');break;case '\\':b.append('\\');break;case '/':b.append('/');break;
                        case 'b':b.append('\b');break;case 'f':b.append('\f');break;case 'n':b.append('\n');break;case 'r':b.append('\r');break;case 't':b.append('\t');break;
                        case 'u':if(i+4>s.length())throw new IllegalArgumentException("Bad unicode escape");b.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;break;
                        default:throw new IllegalArgumentException("Bad string escape");}
                }else b.append(c);
            }throw new IllegalArgumentException("Unterminated JSON string");
        }
        Number number(){
            int start=i;while(i<s.length()&&"-+0123456789.eE".indexOf(s.charAt(i))>=0)i++;
            if(start==i)throw new IllegalArgumentException("Expected JSON value");
            try {String n=s.substring(start,i);return n.contains(".")||n.contains("e")||n.contains("E")?Double.parseDouble(n):Long.parseLong(n);}
            catch(Exception e){throw new IllegalArgumentException("Invalid number");}
        }
    }
}
