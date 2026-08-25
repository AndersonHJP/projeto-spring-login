# Backend Auth API

API REST de autenticação de usuários com Spring Boot, Spring Security e JWT. O banco PostgreSQL roda em container Docker.

## Tecnologias

- Java 17
- Spring Boot 4.1 / Spring Security 7
- JWT (java-jwt)
- JPA / Hibernate
- PostgreSQL 15
- Docker e Docker Compose
- JUnit 5 e Mockito

## Pré-requisitos

- JDK 17+
- Docker Desktop
- Maven 3.9+ (ou o wrapper `./mvnw`)

## Configuração

### Variáveis de ambiente

| Variável | Obrigatória | Padrão | Descrição |
|---|---|---|---|
| `DB_URL` | Não | `jdbc:postgresql://localhost:5432/userdb` | URL de conexão |
| `DB_USERNAME` | Não | `postgres` | Usuário do banco |
| `DB_PASSWORD` | **Sim** | — | Senha do banco |
| `JWT_SECRET` | **Sim** | — | Chave de assinatura do token (mín. 32 caracteres) |
| `JWT_EXPIRATION` | Não | `86400000` | Validade do token em ms (24h) |

`DB_PASSWORD` e `JWT_SECRET` não têm valor padrão: a aplicação não sobe sem elas.

### Docker (`.env`)

Crie um `.env` na raiz, ao lado do `docker-compose.yml`:

```
DB_NAME=userdb
DB_USERNAME=admin
DB_PASSWORD=admin123
```

Esse arquivo é lido apenas pelo Docker Compose. Adicione-o ao `.gitignore`.

### IDE

O IntelliJ não lê o `.env`. Em **Run → Edit Configurations → Environment variables**:

```
DB_USERNAME=admin;DB_PASSWORD=admin123;JWT_SECRET=sua-chave-com-32-caracteres-ou-mais
```

## Subindo o banco

```bash
docker compose up -d
```

Sobe o PostgreSQL 15 na porta `5432`, com volume persistente e healthcheck.

```bash
docker compose ps       # conferir status
docker compose logs -f  # acompanhar logs
docker compose down     # parar (dados preservados)
docker compose down -v  # parar e APAGAR os dados
```

> Se a porta 5432 já estiver ocupada por um PostgreSQL instalado no sistema, pare o serviço nativo ou altere o mapeamento para `5433:5432` no `docker-compose.yml` e ajuste `DB_URL`.

## Rodando a aplicação

```bash
./mvnw spring-boot:run
```

Disponível em `http://localhost:8080`. O schema é criado automaticamente pelo Hibernate (`ddl-auto=update`).

## Endpoints

| Método | Endpoint | Descrição | Autenticação |
|---|---|---|---|
| POST | `/api/users/register` | Criar novo usuário | Não |
| POST | `/api/auth/login` | Autenticar usuário | Não |
| GET | `/api/users/me` | Obter dados do usuário logado | Sim |

### 1. Registrar usuário

```http
POST /api/users/register
Content-Type: application/json

{
  "name": "Anderson",
  "email": "anderson@example.com",
  "password": "12345678"
}
```

**201 Created**

```json
{
  "id": 1,
  "name": "Anderson",
  "email": "anderson@example.com"
}
```

### 2. Login

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "anderson@example.com",
  "password": "12345678"
}
```

**200 OK**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 86400000
}
```

### 3. Usuário logado

```http
GET /api/users/me
Authorization: Bearer <token>
```

**200 OK**

```json
{
  "id": 1,
  "name": "Anderson",
  "email": "anderson@example.com"
}
```


## Regras de negócio

### Registro

- O e-mail deve ser único; se já existir, retorna **409 Conflict**
- A senha deve ter no mínimo 8 caracteres
- A senha é armazenada como hash BCrypt (custo 10) — nunca em texto puro
- A resposta nunca expõe a senha

### Login

- O usuário é localizado pelo e-mail e a senha é conferida via BCrypt
- Credencial inválida retorna **401 Unauthorized** com mensagem genérica (`Invalid email or password`), sem distinguir e-mail inexistente de senha errada — proteção contra enumeração de usuários
- Em caso de sucesso, é emitido um JWT assinado em HMAC256, válido por 24 horas

### Acesso autenticado

- Rotas públicas: `/api/auth/login` e `/api/users/register`
- Todas as demais exigem o header `Authorization: Bearer <token>`
- Sessão stateless, sem cookies, com CSRF desabilitado
- Token ausente, inválido ou expirado resulta em **401**

## Tratamento de erros

Erros são tratados globalmente por `@RestControllerAdvice` e seguem um formato único:

```json
{
  "timestamp": "2026-08-25T14:32:11.482",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for one or more fields.",
  "errors": {
    "password": "The password must be at least 8 characters long."
  }
}
```

O campo `errors` aparece apenas em falhas de validação de campo.

| Status | Situação |
|---|---|
| 400 | Falha de validação nos dados enviados |
| 401 | Credencial inválida, ou token ausente/inválido |
| 403 | Autenticado, mas sem permissão para o recurso |
| 409 | E-mail já cadastrado |

## Testes

```bash
./mvnw test
```

Testes unitários com JUnit 5 e Mockito cobrindo os cenários de sucesso e falha de `UserAccountService.register()` e `AuthorizationService.login()`.

## Estrutura

```
backend-auth/
├── docker-compose.yml
├── Dockerfile
├── .env                 (não versionado)
├── pom.xml
└── src/
    ├── main/java/com/familyti/product/
    │   ├── config/      SecurityConfiguration, JwtAuthFilter, TokenService
    │   ├── controller/  AuthenticationController, UserAccountController
    │   ├── dto/
    │   ├── exception/   GlobalExceptionHandler e exceções de domínio
    │   ├── model/
    │   ├── repository/
    │   └── service/
    └── test/java/com/familyti/product/
```
