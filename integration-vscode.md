## Visual Studio Code integration

MDLZ80 pattern-based optimizer can be integrated into VSCode using a problem matcher. Here is a task definition (_.vscode/tasks.json_) example:

```json
{
	"version": "2.0.0",
	"tasks": [{
		"label": "run mdlz80",
		"type": "shell",
		"command": "java -jar mdlz80.jar <main source> -po -dialect <dialect>",
		"group": "build",
		"problemMatcher": {
			"applyTo": "allDocuments",
			"fileLocation": [
				"autodetect",
				"${workspaceFolder}"
			],
			"pattern": [
				{
					"regexp": "^(\\w+): Pattern-based optimization in (.+)#([0-9]+): (.+) \\(\\d+ bytes saved\\)$",
					"file": 2,
					"line": 3,
					"severity": 1,
					"message": 4
				}
			]
		}
	}]
}
```
