import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { vi } from "vitest";
import LoginPage from "./LoginPage";

const navigateMock = vi.fn();
const loginMock = vi.fn();

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return { ...actual, useNavigate: () => navigateMock };
});

vi.mock("../apiClient", () => ({
  api: {
    login: (...args) => loginMock(...args),
  },
}));

describe("LoginPage", () => {
  it("fills demo credentials when fill button clicked", () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getAllByText("Fill")[0]);
    expect(screen.getByPlaceholderText("Email")).toHaveValue("officer@gov.sg");
    expect(screen.getByPlaceholderText("Password")).toHaveValue("password");
  });

  it("navigates to officer dashboard on successful login", async () => {
    loginMock.mockResolvedValueOnce({
      token: "token",
      role: "OFFICER",
    });

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByPlaceholderText("Email"), { target: { value: "officer@gov.sg" } });
    fireEvent.change(screen.getByPlaceholderText("Password"), { target: { value: "password" } });
    fireEvent.click(screen.getByText("Sign in"));

    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith("/officer/dashboard"));
  });
});
