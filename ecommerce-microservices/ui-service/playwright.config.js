import { defineConfig } from '@playwright/test';
import fs from 'node:fs';

// Prefer a system-provided Chromium (e.g. sandboxed CI/dev containers that
// pre-install one); otherwise fall back to Playwright's own download.
const systemChromium = process.env.CHROMIUM_PATH || '/opt/pw-browsers/chromium';
const executablePath = fs.existsSync(systemChromium) ? systemChromium : undefined;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    launchOptions: executablePath ? { executablePath } : {},
  },
  webServer: {
    command: 'npm run dev -- --port 5173 --strictPort',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
});
