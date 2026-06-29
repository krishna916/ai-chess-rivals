# Frontend Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Initialize the frontend workspace with Tailwind CSS v4, path aliases, shadcn/ui, specified folders, and third-party libraries.

**Architecture:** Configure `@tailwindcss/vite` plugin for CSS-first Tailwind configuration, setup TypeScript and Vite path aliases, initialize shadcn/ui, install specified components, create structured subdirectories, and install required libraries.

**Tech Stack:** React 19, Vite 8, Tailwind CSS v4, shadcn/ui, TypeScript.

## Global Constraints

- Use Tailwind CSS v4 with `@tailwindcss/vite` plugin.
- Configure `@/*` path alias mapping to `src/*`.
- Only install the requested shadcn components: Button, Card, Badge, Dialog, Scroll Area.
- Create 8 specified subfolders inside `src/`.

---

### Task 1: Install Dependencies

**Files:**
- Modify: `package.json`

**Interfaces:**
- Consumes: None
- Produces: Installed packages in `node_modules`

- [ ] **Step 1: Install production dependencies**

Run:
```powershell
npm install react-router-dom react-chessboard chess.js zustand axios lucide-react dayjs tailwindcss-animate
```
Expected output: Packages successfully installed.

- [ ] **Step 2: Install Tailwind CSS v4 and Node types as devDependencies**

Run:
```powershell
npm install -D tailwindcss @tailwindcss/vite @types/node
```
Expected output: Tailwind CSS v4 and dev dependencies successfully installed.

- [ ] **Step 3: Commit**

Run:
```powershell
git add package.json package-lock.json
git commit -m "chore: install frontend dependencies"
```

---

### Task 2: Configure Path Aliases and Tailwind CSS v4

**Files:**
- Modify: `vite.config.ts`
- Modify: `tsconfig.app.json`
- Modify: `src/index.css`

**Interfaces:**
- Consumes: Installed node modules
- Produces: Enabled `@/*` path routing and Tailwind compiler integration

- [ ] **Step 1: Update vite.config.ts**

Replace the contents of `D:\projects\ai-chess-rivals\frontend\vite.config.ts` with:
```typescript
import path from "path"
import { defineConfig } from "vite"
import react from "@vitejs/plugin-react"
import tailwindcss from "@tailwindcss/vite"

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
})
```

- [ ] **Step 2: Update tsconfig.app.json**

Replace the contents of `D:\projects\ai-chess-rivals\frontend\tsconfig.app.json` with:
```json
{
  "compilerOptions": {
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.app.tsbuildinfo",
    "target": "es2023",
    "lib": ["ES2023", "DOM"],
    "module": "esnext",
    "types": ["vite/client"],
    "skipLibCheck": true,

    /* Path Aliases */
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    },

    /* Bundler mode */
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "verbatimModuleSyntax": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",

    /* Linting */
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "erasableSyntaxOnly": true,
    "noFallthroughCasesInSwitch": true
  },
  "include": ["src"]
}
```

- [ ] **Step 3: Update src/index.css**

Replace the contents of `D:\projects\ai-chess-rivals\frontend\src\index.css` with:
```css
@import "tailwindcss";
```

- [ ] **Step 4: Verify Vite build**

Run:
```powershell
npm run build
```
Expected output: Vite compilation completes successfully without path or TypeScript resolution errors.

- [ ] **Step 5: Commit changes**

Run:
```powershell
git add vite.config.ts tsconfig.app.json src/index.css
git commit -m "config: configure path aliases and tailwind v4"
```

---

### Task 3: Initialize shadcn/ui

**Files:**
- Create: `components.json`
- Modify: `src/index.css`

**Interfaces:**
- Consumes: Configuration in `tsconfig.app.json` and `vite.config.ts`
- Produces: Initialized `components.json` configured for Vite & Tailwind v4

- [ ] **Step 1: Run shadcn/ui init command**

Run:
```powershell
npx shadcn@latest init -t vite -y
```
Expected output: Success message confirming initialization of shadcn/ui.

- [ ] **Step 2: Verify components.json contents**

Check `D:\projects\ai-chess-rivals\frontend\components.json` to ensure aliases match `@/components` etc., and tailwind config is blank `""` for Tailwind v4 compatibility.

- [ ] **Step 3: Commit**

Run:
```powershell
git add components.json src/index.css
git commit -m "chore: initialize shadcn/ui"
```

---

### Task 4: Add Requested Components

**Files:**
- Create: `src/components/ui/button.tsx`
- Create: `src/components/ui/card.tsx`
- Create: `src/components/ui/badge.tsx`
- Create: `src/components/ui/dialog.tsx`
- Create: `src/components/ui/scroll-area.tsx`
- Modify: `src/lib/utils.ts`

**Interfaces:**
- Consumes: shadcn CLI
- Produces: UI component components available via `@/components/ui/*`

- [ ] **Step 1: Install components via shadcn CLI**

Run:
```powershell
npx shadcn@latest add button card badge dialog scroll-area -y
```
Expected output: Confirmation of installation of the five components and utility files.

- [ ] **Step 2: Verify component file structure**

Check if the following files are created:
- `D:\projects\ai-chess-rivals\frontend\src\components\ui\button.tsx`
- `D:\projects\ai-chess-rivals\frontend\src\components\ui\card.tsx`
- `D:\projects\ai-chess-rivals\frontend\src\components\ui\badge.tsx`
- `D:\projects\ai-chess-rivals\frontend\src\components\ui\dialog.tsx`
- `D:\projects\ai-chess-rivals\frontend\src\components\ui\scroll-area.tsx`
- `D:\projects\ai-chess-rivals\frontend\src\lib\utils.ts`

- [ ] **Step 3: Commit**

Run:
```powershell
git add src/components/ui/ src/lib/utils.ts
git commit -m "feat: add shadcn components"
```

---

### Task 5: Create Folders Structure

**Files:**
- Create directories under `src/`

**Interfaces:**
- Consumes: None
- Produces: Project folder layout for features, pages, hooks, services, store, types

- [ ] **Step 1: Create directories**

Run:
```powershell
mkdir src/components -ErrorAction SilentlyContinue
mkdir src/features -ErrorAction SilentlyContinue
mkdir src/pages -ErrorAction SilentlyContinue
mkdir src/hooks -ErrorAction SilentlyContinue
mkdir src/lib -ErrorAction SilentlyContinue
mkdir src/services -ErrorAction SilentlyContinue
mkdir src/store -ErrorAction SilentlyContinue
mkdir src/types -ErrorAction SilentlyContinue
```
Expected output: Directories successfully created.

- [ ] **Step 2: Add placeholder .gitkeep files to keep empty folders in git**

Create `.gitkeep` files in folders that might be empty:
- `src/features/.gitkeep`
- `src/pages/.gitkeep`
- `src/hooks/.gitkeep`
- `src/services/.gitkeep`
- `src/store/.gitkeep`
- `src/types/.gitkeep`

- [ ] **Step 3: Commit**

Run:
```powershell
git add src/
git commit -m "chore: create folder structure"
```

---

### Task 6: Final Verification

**Files:**
- Modify: `src/App.tsx`

**Interfaces:**
- Consumes: Installed components and libraries
- Produces: Working application importing the configured packages

- [ ] **Step 1: Update App.tsx to verify component imports and rendering**

Replace the contents of `D:\projects\ai-chess-rivals\frontend\src\App.tsx` with:
```tsx
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"

function App() {
  return (
    <div className="flex items-center justify-center min-h-screen bg-neutral-50 dark:bg-neutral-900 p-4">
      <Card className="w-[380px] shadow-lg">
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-xl font-bold">Chess Rivals Setup</CardTitle>
          <Badge variant="outline">v0.0.0</Badge>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-neutral-500 dark:text-neutral-400">
            Frontend setup is complete. Tailwind CSS v4, shadcn/ui components, and structure are verified.
          </p>
          <div className="flex gap-2">
            <Button className="w-full">Get Started</Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

export default App
```

- [ ] **Step 2: Run a build to verify compilation**

Run:
```powershell
npm run build
```
Expected output: Build runs successfully with zero errors.

- [ ] **Step 3: Commit verification**

Run:
```powershell
git add src/App.tsx
git commit -m "test: verify build and components configuration"
```
