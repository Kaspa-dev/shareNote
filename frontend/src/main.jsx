import React from "react";
import { BrowserRouter } from "react-router";
import { createRoot } from "react-dom/client";
import "@fontsource-variable/manrope";
import App from "./App.jsx";
import "./styles/global.css";
createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <BrowserRouter><App /></BrowserRouter>
  </React.StrictMode>,
);
