package com.example.aichalangemcpserver.presentation

object HtmlPageRenderer {
    fun page(): String = """
        <!doctype html>
        <html lang="ru">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>Инструменты MCP</title>
          <style>
            :root {
              --bg: #f5f7fb;
              --text: #1d2433;
              --card: #ffffff;
              --line: #d8dfeb;
              --accent: #0b5fff;
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
            h1 { margin: 0 0 8px; }
            .hint { margin: 0 0 18px; opacity: .75; }
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
            <div id="tools" class="grid"></div>
          </main>
          <script>
            const container = document.getElementById("tools");
            fetch("/api/tools")
              .then((response) => response.json())
              .then((tools) => {
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
              })
              .catch((error) => {
                container.innerHTML = `<div class="card">Не удалось загрузить инструменты: ${'$'}{error}</div>`;
              });
          </script>
        </body>
        </html>
    """.trimIndent()
}
