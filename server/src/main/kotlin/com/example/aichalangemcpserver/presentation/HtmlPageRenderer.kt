package com.example.aichalangemcpserver.presentation

object HtmlPageRenderer {
    fun page(): String = """
        <!doctype html>
        <html lang="ru">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>Доступные инструменты MCP</title>
          <style>
            :root {
              --bg: #f5f7fb;
              --text: #1d2433;
              --card: #ffffff;
              --line: #d8dfeb;
              --accent: #0b5fff;
              --danger: #e03e3e;
            }
            * { box-sizing: border-box; }
            body {
              margin: 0;
              font-family: "Segoe UI", Tahoma, Geneva, Verdana, sans-serif;
              color: var(--text);
              background: radial-gradient(circle at 20% 20%, #e7eefc 0, var(--bg) 45%);
              min-height: 100vh;
            }
            main {
              max-width: 960px;
              margin: 0 auto;
              padding: 24px;
            }
            .toolbar {
              display: flex;
              gap: 10px;
              flex-wrap: wrap;
              margin: 14px 0 18px;
            }
            button {
              border: 0;
              border-radius: 8px;
              padding: 10px 14px;
              color: #fff;
              cursor: pointer;
              font-weight: 600;
              background: var(--accent);
            }
            button.danger {
              background: var(--danger);
            }
            .hint { margin: 0; opacity: .75; }
            .status { margin: 6px 0 0; min-height: 20px; font-size: 14px; }
            .grid {
              display: grid;
              gap: 12px;
            }
            .card {
              border: 1px solid var(--line);
              border-radius: 12px;
              background: var(--card);
              padding: 14px;
              box-shadow: 0 8px 20px rgba(19, 37, 68, .05);
            }
            .tool-name {
              font-family: Consolas, Monaco, "Courier New", monospace;
              font-size: 15px;
              color: var(--accent);
            }
            .tool-description {
              margin: 8px 0 10px;
            }
            pre {
              margin: 0;
              padding: 10px;
              border-radius: 8px;
              overflow: auto;
              background: #f2f5fb;
              border: 1px solid var(--line);
              font-size: 12px;
            }
          </style>
        </head>
        <body>
          <main>
            <h1>Доступные инструменты MCP</h1>
            <p class="hint">Источник данных: <code>/api/tools</code></p>
            <div class="toolbar">
              <button id="reloadBtn" type="button">Обновить список инструментов</button>
              <button id="clearDbBtn" type="button" class="danger">Очистить базу данных</button>
            </div>
            <p class="status" id="status"></p>
            <div id="tools" class="grid"></div>
          </main>
          <script>
            const container = document.getElementById("tools");
            const statusEl = document.getElementById("status");
            const reloadBtn = document.getElementById("reloadBtn");
            const clearDbBtn = document.getElementById("clearDbBtn");

            function setStatus(text, isError = false) {
              statusEl.textContent = text || "";
              statusEl.style.color = isError ? "#c62828" : "#1d2433";
            }

            function renderTools(tools) {
              if (!Array.isArray(tools) || tools.length === 0) {
                container.innerHTML = '<div class="card">Инструменты не зарегистрированы</div>';
                return;
              }

              container.innerHTML = tools.map((tool) => `
                <article class="card">
                  <div class="tool-name">${'$'}{tool.name}</div>
                  <p class="tool-description">${'$'}{tool.description}</p>
                  <pre>${'$'}{JSON.stringify(tool.inputSchema, null, 2)}</pre>
                </article>
              `).join("");
            }

            async function loadTools() {
              setStatus("Загрузка инструментов...");
              try {
                const response = await fetch("/api/tools");
                const tools = await response.json();
                renderTools(tools);
                setStatus("Список инструментов обновлен");
              } catch (error) {
                container.innerHTML = `<div class="card">Не удалось загрузить инструменты: ${'$'}{error}</div>`;
                setStatus("Ошибка загрузки инструментов", true);
              }
            }

            async function clearDatabase() {
              setStatus("Очистка базы данных...");
              try {
                const response = await fetch("/api/notes/clear", { method: "POST" });
                if (!response.ok) {
                  throw new Error(`HTTP ${'$'}{response.status}`);
                }
                const result = await response.json();
                setStatus(`База данных очищена. Удалено записей: ${'$'}{result.deleted}`);
              } catch (error) {
                setStatus(`Не удалось очистить базу данных: ${'$'}{error}`, true);
              }
            }

            reloadBtn.addEventListener("click", loadTools);
            clearDbBtn.addEventListener("click", clearDatabase);
            loadTools();
          </script>
        </body>
        </html>
    """.trimIndent()
}
