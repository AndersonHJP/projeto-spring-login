# Backend Auth API

API REST de autenticação de usuários com Spring Boot, Spring Security e JWT. O banco PostgreSQL roda em container Docker.

## Tecnologias

- Java 17
- Spring Boot 4.1 / Spring Security 7
- JWT (java-jwt)
- JPA / Hibernate
- PostgreSQL 15
- AWS SDK for Java 2.x (Amazon S3)
- MinIO (armazenamento S3-compatível)
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
| `STORAGE_ENABLED` | Não | valor de `STORAGE_PROVIDER` | Provedores de que dá para ler, separados por vírgula: `minio`, `s3` ou `minio,s3` |
| `STORAGE_PROVIDER` | Não | `minio` | Destino das fotos novas: `s3` ou `minio` |

`DB_PASSWORD` e `JWT_SECRET` não têm valor padrão: a aplicação não sobe sem elas.

As variáveis de storage estão descritas em [Armazenamento de fotos](#armazenamento-de-fotos-s3-ou-minio).
Copie `.env.example` para `.env` e ajuste os valores — o `.env` não é versionado.



Em **Run → Edit Configurations → Environment variables**:

```
DB_USERNAME=admin;DB_PASSWORD=admin123;JWT_SECRET=sua-chave-com-32-caracteres-ou-mais
```

## Subindo a infraestrutura

```bash
docker compose up -d
```

Sobe os serviços do `docker-compose.yml`, com volume persistente e healthcheck:

| Serviço | Portas | Descrição |
|---|---|---|
| `db` | `5433:5432` | PostgreSQL 15 |
| `minio` | `9000` (API), `9001` (console) | Storage S3-compatível local |
| `backend` | `8080` | A aplicação |

O `backend` só inicia depois de `db` e `minio` estarem saudáveis.

```bash
docker compose ps       # conferir status
docker compose logs -f  # acompanhar logs
docker compose down     # parar (dados preservados)
docker compose down -v  # parar e APAGAR os dados
```

> O Postgres é publicado em `5433` justamente para não conflitar com uma instalação nativa na `5432`. Se `5433`, `9000` ou `9001` já estiverem ocupadas, ajuste o mapeamento no `docker-compose.yml` e as variáveis correspondentes.

## Armazenamento de fotos (S3 ou MinIO)

São **duas decisões separadas**, e confundi-las é a origem da maioria dos problemas:

| Property | Variável | O que decide |
|---|---|---|
| `storage.enabled` | `STORAGE_ENABLED` | De quais provedores dá para **ler**. Lista separada por vírgula. |
| `storage.provider` | `STORAGE_PROVIDER` | Para onde vai uma foto **nova**. Um só, e tem de constar em `storage.enabled`. |

Os valores aceitos são os backends declarados em `storage.backends.<nome>` no `application.properties`:

| Valor | Destino |
|---|---|
| `s3` | Amazon S3 (nuvem) |
| `minio` | MinIO local, via Docker Compose |

Os valores não diferenciam maiúsculas de minúsculas e espaços ao redor são ignorados (`MinIO`, ` minio ` e `minio` são equivalentes). `STORAGE_ENABLED` ausente significa "apenas o `STORAGE_PROVIDER`" — o comportamento de um provedor por execução.

> **Provedor novo é configuração, não código.** Amazon S3, MinIO, Cloudflare R2, DigitalOcean Spaces e Wasabi falam o mesmo protocolo, então existe uma única implementação — `S3CompatibleStorageStrategy` — parametrizada por endpoint, bucket e credenciais. Adicionar um backend é acrescentar um bloco `storage.backends.<nome>.*` e citá-lo em `STORAGE_ENABLED`; não há classe, bean nem condicional por provedor.

O `PhotoService` conhece apenas a interface `StorageStrategy` e recebe um `StorageRegistry`: as strategies habilitadas indexadas pelo nome do backend, mais o destino das gravações novas.

> **Cada foto é atendida pelo provedor em que foi gravada**, não pelo destino das fotos novas. Ler, substituir o binário e apagar seguem a coluna `photos.storage_provider` (nas linhas antigas, deduzida da URL canônica em `photos.s3_url`). Usar o provedor ativo apagaria uma chave inexistente no outro bucket e deixaria o arquivo original órfão.

> **Habilitar dois é dual-read, nunca dual-write.** Uma foto vai para um provedor só. Sem escolha do cliente vale o `storage.provider`; o `POST /api/photos` aceita o parâmetro `storageProvider` para mandar aquele upload para outro provedor habilitado. Um valor que o servidor não aceita responde **400** — nunca grava no destino padrão calado. Não há cópia nem fallback silencioso.

> **A foto de um provedor não habilitado não some** — ela aparece na listagem com `url` nula e o cliente a marca como indisponível. Operações que mexem no objeto (`PUT` com arquivo, `DELETE`) são recusadas com mensagem explícita, para não remover a linha e abandonar o arquivo no bucket.

> **Trocar o toggle não migra as fotos já gravadas.** Os arquivos continuam onde foram enviados. A migração, se necessária, é manual.

### Modo MinIO (desenvolvimento local)

É o padrão: sem `STORAGE_PROVIDER` definido, a aplicação usa o MinIO e não exige conta AWS. Basta subir a infraestrutura:

```bash
docker compose up -d
```

```
STORAGE_PROVIDER=minio
```

O `docker compose up` sobe o MinIO com API em `9000` e console em `9001`. O bucket da aplicação é criado pelo próprio backend no startup (`BucketInitializer`, ligado por `storage.backends.minio.create-bucket-if-missing`), de forma idempotente — então a subida funciona igual dentro do Compose e fora dele (`./mvnw spring-boot:run` contra o MinIO do Docker).

Console web: **http://localhost:9001** — autentique com `MINIO_ROOT_USER` e `MINIO_ROOT_PASSWORD`.

| Variável | Padrão | Descrição |
|---|---|---|
| `MINIO_ROOT_USER` | — | Usuário root do container MinIO |
| `MINIO_ROOT_PASSWORD` | — | Senha root do container MinIO |
| `MINIO_ENDPOINT` | `http://localhost:9000` | Endpoint usado pela aplicação |
| `MINIO_BUCKET` | `app-photos-bucket` | Bucket das fotos |
| `MINIO_ACCESS_KEY` | `minioadmin` | Credencial que a aplicação usa |
| `MINIO_SECRET_KEY` | `minioadmin` | Credencial que a aplicação usa |

Rodando a aplicação **dentro** do Compose, o endpoint correto é `http://minio:9000` — o `docker-compose.yml` já injeta esse valor. **Fora** do Docker (`./mvnw spring-boot:run`), use `http://localhost:9000`.

### Modo Amazon S3 (produção / homologação)

```
STORAGE_PROVIDER=s3
```

| Variável | Obrigatória | Descrição |
|---|---|---|
| `AWS_S3_BUCKET` | **Sim** | Nome do bucket |
| `AWS_S3_REGION` | **Sim** | Região do bucket (ex.: `us-east-2`) |
| `AWS_ACCESS_KEY_ID` | — | Credencial AWS; omita se usar IAM role |
| `AWS_SECRET_ACCESS_KEY` | — | Credencial AWS; omita se usar IAM role |

As credenciais são resolvidas pela cadeia padrão do SDK (`DefaultCredentialsProvider`), então em ambientes com IAM role basta não definir as chaves. Com `STORAGE_PROVIDER=s3` o serviço MinIO pode continuar no Compose — ele simplesmente não é usado.

### Falhas de configuração

A validação é **fail-fast**: a aplicação não sobe com o toggle inconsistente. As mensagens citam a variável de ambiente a corrigir.

| Situação | Resultado |
|---|---|
| Nenhum provedor habilitado | Falha no startup |
| Valor desconhecido (ex.: `xpto`) em qualquer das duas | Falha no startup |
| `STORAGE_PROVIDER` fora de `STORAGE_ENABLED` | Falha no startup |
| Property obrigatória de **qualquer** provedor habilitado em branco (inclusive por variável de ambiente não definida) | Falha no startup |
| Provedor habilitado sem bloco `storage.backends.<nome>` declarado | Falha no startup |
| Backend com `endpoint` próprio sem `access-key`/`secret-key` | Falha no startup |

A quarta linha é a que costuma surpreender: habilitar o `s3` só para leitura ainda exige `AWS_S3_BUCKET` e `AWS_S3_REGION`. É deliberado — sem isso a falha só apareceria na primeira foto lida da nuvem.

### URLs das fotos

A leitura usa **URLs pré-assinadas com validade de 15 minutos** nos dois provedores (`storage.backends.<nome>.presign-expiration-minutes`). O bucket não precisa ser público.

Cada URL é assinada pelo provedor **daquela foto**, então com os dois habilitados o host varia de uma para outra dentro da mesma listagem. `PhotoResponse.url` vem nula quando o provedor da foto não está habilitado.

> Nenhuma credencial de storage fica no código — todas vêm do ambiente. Não exponha o console do MinIO sem autenticação em ambiente compartilhado.

## Rodando a aplicação

A aplicação **lê o `.env` da raiz do projeto automaticamente**, via
`spring.config.import=optional:file:./.env[.properties]`. Basta ter o arquivo no lugar:

```bash
./mvnw spring-boot:run
```

Pela IDE funciona igual, sem precisar preencher **Environment variables** na run
configuration. Variáveis de ambiente reais têm precedência sobre o arquivo, então dentro do
Compose valem as do container, e em produção valem as do ambiente. O `optional:` faz o
arquivo ser ignorado quando não existe (CI, container, produção).

Sem o `.env` e sem variáveis no ambiente, a aplicação não sobe: `DB_PASSWORD` e
`JWT_SECRET` não têm valor padrão. Copie `.env.example` para `.env` antes do primeiro run.

> **A porta 8080 é uma só.** O `docker compose up -d` sobe também o serviço `backend`, que
> publica a 8080. Com ele no ar, a aplicação na IDE não consegue fazer o bind e falha com
> *"Port 8080 was already in use"*. Para rodar pela IDE, libere a porta mantendo o resto de pé:
>
> ```bash
> docker compose stop backend
> ```

Disponível em `http://localhost:8080`. O schema é criado automaticamente pelo Hibernate
(`ddl-auto=update`).

## Endpoints

| Método | Endpoint | Descrição | Autenticação |
|---|---|---|---|
| POST | `/api/users/register` | Criar novo usuário | Não |
| POST | `/api/auth/login` | Autenticar usuário | Não |
| GET | `/api/users/me` | Obter dados do usuário logado | Sim |
| GET | `/api/storage` | Provedor de storage ativo nesta execução | Sim |

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
| 413 | Arquivo acima do limite de upload (`spring.servlet.multipart.max-file-size`) |
| 502 | Falha ao falar com o provedor de storage |

## Testes

```bash
./mvnw test
```

Testes unitários com JUnit 5 e Mockito. Nenhum teste acessa AWS ou MinIO de verdade — os clientes são mockados.

## Estrutura

```
backend-auth/
├── docker-compose.yml  db, minio, backend
├── Dockerfile
├── .env.example         modelo; copie para .env (não versionado)
├── pom.xml
└── src/
    ├── main/java/com/familyti/product/
    │   ├── config/      SecurityConfiguration, JwtAuthFilter, TokenService
    │   ├── controller/  AuthenticationController, UserAccountController, PhotoController
    │   ├── dto/
    │   ├── exception/   GlobalExceptionHandler e exceções de domínio
    │   ├── model/
    │   ├── repository/
    │   ├── service/
    │   └── storage/     StorageStrategy e o feature toggle de provedor
    │       ├── StorageStrategy.java               contrato: upload, delete, generateUrl
    │       ├── S3CompatibleStorageStrategy.java   única implementação, serve a todo backend S3
    │       ├── StorageProperties.java             lê e normaliza o toggle e os backends
    │       ├── StorageConfig.java                 valida a config e monta um client por backend
    │       ├── StorageRegistry.java               strategies habilitadas + destino de gravação
    │       └── BucketInitializer.java             garante o bucket na subida, quando pedido
    └── test/java/com/familyti/product/
```
