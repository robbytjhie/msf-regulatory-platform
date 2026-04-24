import { expect, test } from "@playwright/test";

test("shows error message for failed login", async ({ page }) => {
  await page.route("**/api/auth/login", async (route) => {
    await route.fulfill({
      status: 401,
      contentType: "application/json",
      body: JSON.stringify({
        message: "Invalid credentials",
      }),
    });
  });

  await page.goto("/login");
  await page.fill('input[placeholder="Email"]', "officer@gov.sg");
  await page.fill('input[placeholder="Password"]', "password");
  await page.click("text=Sign in");

  await expect(page).toHaveURL(/\/login/);
  await expect(page.locator('input[placeholder="Email"]')).toBeVisible();
});

test("fills demo account values", async ({ page }) => {
  await page.goto("/login");
  await page.getByRole("button", { name: "Fill" }).first().click();

  await expect(page.locator('input[placeholder="Email"]')).toHaveValue("officer@gov.sg");
  await expect(page.locator('input[placeholder="Password"]')).toHaveValue("password");
});
