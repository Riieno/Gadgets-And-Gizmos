# Graph Workshop website assets

Both workspaces ship the complete experimental node catalog. Copy this folder with its `vendor`, `fonts`, `nodes.json`, and `graph-nodes.js` files when updating the website. The existing public-site API and share flow remain supported. A static host can also open and download local graph files without the game server.

The packaged catalog includes resolved ports, node defaults, selector choices, conditional variants, and property types. The editor preserves dynamic ports, target bindings, function interfaces, collection order, full numeric values, and unknown node data during import/export. Live block, physics, worker, and controller values still come from the game.

Regenerate the website catalog from the experimental workspace:

```powershell
.\gradlew.bat generateGraphWebCatalog
```

Copy the resulting `nodes.json` and `graph-nodes.js` into the stable workspace's matching web folder to retain the experimental catalog in both deployments. `graph-nodes.js` is generated from the reusable library resource `assets/gadgetsngizmos/web/graph-nodes.js`; edit its library source and regenerate it.

Run the browser-independent schema and import/export checks from either workspace:

```powershell
node --test src/test/web/graph-editor.test.cjs
```
