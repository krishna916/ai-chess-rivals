import { spawnSync } from "node:child_process";

const npmCli = process.env.npm_execpath;
const checks = ["format:check", "typecheck", "lint", "build"];

if (!npmCli) {
  console.error(
    "npm_execpath is unavailable; run this script through npm run verify.",
  );
  process.exit(1);
}

for (const check of checks) {
  console.log(`\n> npm run ${check}`);
  const result = spawnSync(process.execPath, [npmCli, "run", check], {
    stdio: "inherit",
  });

  if (result.error) {
    console.error(result.error.message);
    process.exit(1);
  }

  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}
