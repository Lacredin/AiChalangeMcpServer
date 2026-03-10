# MCP Server Architecture

## Слои

- `domain`:
  - `McpTool` — контракт инструмента.
  - `ToolDefinition` — метаданные инструмента (имя, описание, JSON Schema входа).
  - `ToolExecutionResult` — результат выполнения.
- `application`:
  - `ToolRegistryService` — реестр инструментов, выдача списка и вызов по имени.
- `infrastructure`:
  - Реализации инструментов. Сейчас добавлен `ExampleEchoTool`.
- `presentation`:
  - `Routes` — HTTP/JSON-RPC API.
  - `HtmlPageRenderer` — веб-страница со списком доступных инструментов.

## Эндпоинты

- `GET /` — HTML-страница со списком инструментов (подгрузка из `/api/tools`).
- `GET /api/tools` — список текущих доступных инструментов.
- `POST /mcp` — JSON-RPC для MCP-методов:
  - `initialize`
  - `tools/list`
  - `tools/call`
- `GET /health` — простая проверка доступности.

## Пример инструмента

- `example.echo`
  - Вход:
    - `message: string` (обязательно)
    - `uppercase: boolean` (опционально)
  - Выход:
    - текст, который возвращается через `tools/call`.
