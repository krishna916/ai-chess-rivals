# Frontend Setup Design Specification - AI Chess Rivals

This design specification outlines the initialization and configuration of the React + Vite frontend workspace using Tailwind CSS v4, shadcn/ui, specified directories, and required third-party libraries.

## 1. Objectives

- Set up Tailwind CSS v4 using the Vite plugin (`@tailwindcss/vite`).
- Configure path aliases (`@/*` pointing to `src/*`) in both Vite and TypeScript configurations.
- Initialize `shadcn/ui` with Vite template settings.
- Add only the requested `shadcn/ui` components: `Button`, `Card`, `Badge`, `Dialog`, and `Scroll Area`.
- Establish the folder structure inside the `src/` directory.
- Install third-party packages needed for frontend features.

---

## 2. Dependency List

The following packages will be installed:
- **Styling**: `tailwindcss`, `@tailwindcss/vite`, `tailwindcss-animate`
- **Component Library**: `shadcn/ui` (and its Radix UI dependencies via CLI)
- **Routing**: `react-router-dom`
- **Chess Logic & UI**: `react-chessboard`, `chess.js`
- **State Management**: `zustand`
- **Utilities & API Client**: `axios`, `lucide-react`, `dayjs`

---

## 3. Configuration & Files

### 3.1 Path Aliases Configuration

#### Vite Configuration (`vite.config.ts`)
We will import Node's `path` package and define resolve aliases.
```typescript
import path from "path"
import { defineConfig } from "vite"
import react from "@vitejs/plugin-react"
import tailwindcss from "@tailwindcss/vite"

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
})
```

#### TypeScript Configuration (`tsconfig.app.json` / `tsconfig.json`)
Update compiler options to support the `@/*` paths mapping:
```json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  }
}
```

### 3.2 Tailwind CSS v4
Tailwind CSS v4 will be integrated via `@tailwindcss/vite`.
In `src/index.css`, standard Tailwind directive will be imported:
```css
@import "tailwindcss";
```

### 3.3 shadcn/ui Initialization
A `components.json` will be generated using:
```bash
npx shadcn@latest init -t vite -y
```
And individual components will be added:
```bash
npx shadcn@latest add button card badge dialog scroll-area -y
```

---

## 4. Folders Structure

The following directory tree will be structured under `src/`:
- `src/components/` - Global reusable presentation components
- `src/features/` - Feature-based modules (e.g., game, matchmaking, dashboard)
- `src/pages/` - Routable pages (routed via `react-router-dom`)
- `src/hooks/` - Custom global React hooks
- `src/lib/` - Third-party client configurations and shared utilities (e.g., `utils.ts` for cn)
- `src/services/` - API service calls (configured with `axios`)
- `src/store/` - Zustand global state stores
- `src/types/` - Shared TypeScript types/interfaces

---

## 5. Verification Plan

1. Run `npm run build` to verify there are no TypeScript or bundler errors.
2. Confirm the selected components exist in `src/components/ui/`.
3. Check the folder layout under `src/`.
