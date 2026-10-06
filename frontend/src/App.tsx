// src/App.tsx
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import { routes } from "./routes/appRoutes";

const router = createBrowserRouter(routes);

export default function App() {
  return <RouterProvider router={router} />;
}
