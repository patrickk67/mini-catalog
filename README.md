# Mini E-Commerce Product Catalog

A beginner-friendly local demo built with HTML, CSS, vanilla JavaScript, and core Java.
No Spring Boot, Maven, Node.js, or database is required.

## Requirements
- JDK 17 or newer (a recent JDK is recommended)
- VS Code (optional, but convenient)

## Run on Windows
1. Extract the ZIP.
2. Open the `mini-ecommerce-catalog` folder in VS Code.
3. Open Terminal → New Terminal.
4. Run:
   ```powershell
   cd backend
   javac Main.java
   java Main
   ```
5. Open http://localhost:8080 in your browser.

The Java server serves both the website and the API, so you do not need Live Server or separate CORS configuration.

## Run on macOS/Linux
```bash
cd backend
javac Main.java
java Main
```
Then open http://localhost:8080.

## Features
- Product catalog loaded from the Java API
- Search, category filter, price sorting
- Product details modal
- Shopping cart with localStorage persistence
- Demo admin page for adding, editing, and deleting products
- Product data stored in `backend/data/products.json`
- Responsive layout for desktop and mobile

## API
- `GET /api/products`
- `GET /api/products/{id}`
- `GET /api/categories`
- `POST /api/products`
- `PUT /api/products/{id}`
- `DELETE /api/products/{id}`

## Notes
- This is a local educational demo, not production e-commerce software.
- The admin page is intentionally not authenticated; do not deploy it publicly.
- Product images use remote Unsplash image URLs, so internet access is needed for those images. The site itself runs locally.
- If port 8080 is already in use, start it on another port, for example `java Main 8181`, then open http://localhost:8181.

Stop the server with Ctrl+C in the terminal.
