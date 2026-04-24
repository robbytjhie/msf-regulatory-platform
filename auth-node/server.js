import Fastify from "fastify";
import cors from "@fastify/cors";
import dotenv from "dotenv";

dotenv.config();

const fastify = Fastify({ logger: true });
await fastify.register(cors, { origin: true });

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";
const PORT = Number(process.env.PORT || 3001);

const profileMap = {
  officer: { email: "officer@gov.sg", password: "password" },
  "operator-acme": { email: "operator@acme.sg", password: "password" },
  "operator-beta": { email: "operator2@beta.sg", password: "password" },
};

fastify.get("/auth/providers", async () => {
  return {
    data: [
      { id: "singpass", label: "Mock Singpass" },
      { id: "corppass", label: "Mock Corppass" },
      { id: "mockpass", label: "Mockpass" },
    ],
  };
});

fastify.post("/auth/mock/:provider/login", async (request, reply) => {
  const provider = String(request.params.provider || "").toLowerCase();
  if (!["singpass", "corppass", "mockpass"].includes(provider)) {
    return reply.code(400).send({ message: "Unsupported provider" });
  }

  const profile = String(request.body?.profile || "officer");
  const creds = profileMap[profile];
  if (!creds) {
    return reply.code(400).send({ message: "Unknown mock profile" });
  }

  const loginResponse = await fetch(`${BACKEND_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(creds),
  });
  const loginJson = await loginResponse.json();

  if (!loginResponse.ok) {
    return reply
      .code(loginResponse.status)
      .send({ message: loginJson?.message || "Backend login failed" });
  }

  return {
    data: {
      ...loginJson.data,
      provider,
      profile,
    },
  };
});

fastify.get("/health", async () => ({ status: "ok" }));

fastify.listen({ port: PORT, host: "0.0.0.0" });
