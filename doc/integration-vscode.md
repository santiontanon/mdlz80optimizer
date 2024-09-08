## Visual Studio Code integration

The output of the MDL pattern-based optimizer can be integrated into VSCode using a problem matcher. Here is a task definition (_.vscode/tasks.json_) example:

```json
{
	"version": "2.0.0",
	"tasks": [{
		"label": "run mdl",
		"type": "shell",
		"command": "java -jar mdl.jar <main source> -po -dialect <dialect>",
		"group": "build",
		"problemMatcher": {
			"applyTo": "allDocuments",
			"fileLocation": [
				"autodetect",
				"${workspaceFolder}"
			],
			"owner": "mdl",
			"pattern": [{
				"regexp": "^(\\w+): (.+) in (.+)#([0-9]+): (.+)$",
				"file": 3,
				"line": 4,
				"severity": 1,
				"message": 5,
				"code": 2
			}]
		},
		"presentation": {
			"echo": false,
			"focus": false,
			"panel": "shared",
			"showReuseMessage": false,
			"clear": true,
			"revealProblems": "onProblem"
		}
	}]
}
```

You can, of course expand this to include other output from MDL, such as style warnings.

Thanks to theNestruo for the initial version and to nataliapc for improvements over it!
