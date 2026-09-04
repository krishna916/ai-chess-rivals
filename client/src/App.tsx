import { HashRouter, Route, Routes } from "react-router-dom";
import { AdminPage } from "./pages/AdminPage";
import { MatchViewerPage } from "./pages/MatchViewerPage";

function App() {
  return (
    <HashRouter>
      <Routes>
        <Route path="/" element={<MatchViewerPage />} />
        <Route path="/admin" element={<AdminPage />} />
      </Routes>
    </HashRouter>
  );
}

export default App;
