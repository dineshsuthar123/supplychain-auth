# Supply Chain Authentication Frontend

A simple React web interface for registering and verifying products on the Supply Chain Authentication System.

## Features
- Register a new product (name, manufacturer, batch)
- Verify a product by Product ID
- Real-time feedback and error handling

## Getting Started

1. Install dependencies:
   ```powershell
   cd frontend
   npm install
   ```
2. Set your backend API endpoint in `.env`:
   ```env
   REACT_APP_API_BASE=https://<your-ingress-endpoint>
   ```
3. Start the development server:
   ```powershell
   npm start
   ```

## Build for Production
```powershell
npm run build
```

---

This UI is designed for easy extension (e.g., add QR scan, product history, admin dashboard).
