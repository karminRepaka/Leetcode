// src/services/adminSystemConfigService.jsx

// Assumptions based on your Spring controller screenshot:
// POST /Configurations
// params: category, configName, userid, excelDown
//
// You mentioned: Save + History exist (from old Angular controller screenshots).
// If your endpoints differ, update the paths below:
// - SAVE: POST /Configurations/save (example)  -> adjust to your actual endpoint
// - HISTORY: POST /Configurations/history (example) -> adjust to your actual endpoint

function buildUrlWithParams(path, params) {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v === undefined || v === null) return;
    qs.append(k, String(v));
  });
  return `${path}?${qs.toString()}`;
}

async function safeReadJson(res) {
  const text = await res.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

async function requestJson(url, options = {}) {
  const res = await fetch(url, { credentials: "include", ...options });
  const data = await safeReadJson(res);
  if (!res.ok) {
    const msg = (data && (data.message || data.error)) || `Request failed: ${res.status}`;
    throw new Error(msg);
  }
  return data;
}

export function createAdminSystemConfigService({ baseUrl = "" } = {}) {
  const API = {
    // Search list (JSON)
    search: async ({ category = "", configName = "", userid }) => {
      if (!userid) throw new Error("userid is required");

      const url = buildUrlWithParams(`${baseUrl}/Configurations`, {
        category,
        configName,
        userid,
        excelDown: false,
      });

      // backend expects POST even for fetching
      return requestJson(url, { method: "POST" });
    },

    // Excel download (blob)
    downloadExcel: async ({ category = "", configName = "", userid }) => {
      if (!userid) throw new Error("userid is required");

      const url = buildUrlWithParams(`${baseUrl}/Configurations`, {
        category,
        configName,
        userid,
        excelDown: true,
      });

      const res = await fetch(url, { method: "POST", credentials: "include" });
      if (!res.ok) throw new Error(`Excel download failed: ${res.status}`);

      const blob = await res.blob();
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = "admin-system-config.xlsx";
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(a.href);
    },

    // CREATE (adjust endpoint if needed)
    // If your backend reuses /Configurations with different params/body, update accordingly.
    create: async ({ category, configName, configValue, userid }) => {
      if (!userid) throw new Error("userid is required");
      if (!category || !configName || !configValue) throw new Error("category, configName, configValue required");

      // 👉 Replace with your real create endpoint if different:
      // Example: POST /Configurations/create
      const url = `${baseUrl}/Configurations/create`;

      return requestJson(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ category, configName, configValue, userid }),
      });
    },

    // SAVE edited values (adjust endpoint if needed)
    save: async ({ updates, userid }) => {
      if (!userid) throw new Error("userid is required");
      if (!Array.isArray(updates) || updates.length === 0) throw new Error("No changes to save");

      // 👉 Replace with your real save endpoint if different:
      // Example: POST /Configurations/save?userid=...&type=edit
      const url = buildUrlWithParams(`${baseUrl}/Configurations/save`, { userid, type: "edit" });

      return requestJson(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ configInfoArr: updates }),
      });
    },

    // HISTORY popup list (adjust endpoint if needed)
    history: async ({ category, configName, userid }) => {
      if (!userid) throw new Error("userid is required");

      // 👉 Replace with your real history endpoint if different:
      const url = buildUrlWithParams(`${baseUrl}/Configurations/history`, {
        category,
        configName,
        userid,
      });

      return requestJson(url, { method: "POST" });
    },
  };

  return API;
}


// src/pages/AdminSystemConfig.jsx

import React, { useEffect, useMemo, useState } from "react";
import { createAdminSystemConfigService } from "../services/adminSystemConfigService";
import { useIdpContext } from "../contexts/idpcontext"; // adjust if your hook name differs
import config from "../lib/config"; // adjust if your config export differs

function normalizeSearchResponse(resp) {
  // Your old Angular code did: JSON.parse(results[0]) and then rel.result.retData.configList
  // Here we try to handle common shapes safely.

  if (!resp) return { rows: [], count: 0 };

  // case 1: already list
  if (Array.isArray(resp)) return { rows: resp, count: resp.length };

  // case 2: resp.result.retData.configList
  const rows1 = resp?.result?.retData?.configList;
  if (Array.isArray(rows1)) return { rows: rows1, count: rows1.length };

  // case 3: resp.retData.configList
  const rows2 = resp?.retData?.configList;
  if (Array.isArray(rows2)) return { rows: rows2, count: rows2.length };

  // case 4: resp.configList
  const rows3 = resp?.configList;
  if (Array.isArray(rows3)) return { rows: rows3, count: rows3.length };

  // fallback
  return { rows: [], count: 0 };
}

function normalizeHistoryResponse(resp) {
  // UI shows: Key Category, Key Name, Key Value, Action, Updated Date, Updated By
  // We'll accept arrays from common shapes.
  if (!resp) return [];
  if (Array.isArray(resp)) return resp;

  const a = resp?.result?.retData?.historyList;
  if (Array.isArray(a)) return a;

  const b = resp?.historyList;
  if (Array.isArray(b)) return b;

  const c = resp?.retData?.historyList;
  if (Array.isArray(c)) return c;

  return [];
}

export default function AdminSystemConfig() {
  // If your project gets userid from idpcontext, use it.
  // Otherwise replace with your own user source.
  const { userInfo } = useIdpContext?.() || {};
  const userid = userInfo?.userId || userInfo?.userid || "";

  const api = useMemo(() => {
    const baseUrl = config?.API_BASE_URL || ""; // set this in src/lib/config.js if needed
    return createAdminSystemConfigService({ baseUrl });
  }, []);

  // Search filters
  const [category, setCategory] = useState("");
  const [configName, setConfigName] = useState("");

  // Create form
  const [showCreate, setShowCreate] = useState(false);
  const [createCategory, setCreateCategory] = useState("");
  const [createKeyName, setCreateKeyName] = useState("");
  const [createKeyValue, setCreateKeyValue] = useState("");

  // Data
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  // Client-side page-size + pagination (since backend doesn’t support paging)
  const [pageSize, setPageSize] = useState(10);
  const [page, setPage] = useState(1);

  // Track edits + selection
  const [editedValues, setEditedValues] = useState(() => new Map()); // key => newValue
  const [selectedKeys, setSelectedKeys] = useState(() => new Set()); // key => selected
  const [originalValues, setOriginalValues] = useState(() => new Map()); // key => original

  // History modal
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyRows, setHistoryRows] = useState([]);
  const [historyTitle, setHistoryTitle] = useState("");

  const totalCount = rows.length;
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));

  const pageRows = useMemo(() => {
    const start = (page - 1) * pageSize;
    return rows.slice(start, start + pageSize);
  }, [rows, page, pageSize]);

  function makeRowKey(r) {
    // stable key for selection/edits
    return `${r.category || ""}|||${r.configName || ""}`;
  }

  async function doSearch() {
    setLoading(true);
    setMessage("");
    setEditedValues(new Map());
    setSelectedKeys(new Set());
    setOriginalValues(new Map());

    try {
      const resp = await api.search({
        category,
        configName,
        userid,
      });

      const { rows: list } = normalizeSearchResponse(resp);

      // Save originals so we can detect change
      const orig = new Map();
      list.forEach((r) => {
        const k = makeRowKey(r);
        orig.set(k, r.configValue);
      });

      setOriginalValues(orig);
      setRows(list);
      setPage(1);
    } catch (e) {
      setRows([]);
      setMessage(e.message || "Search failed");
    } finally {
      setLoading(false);
    }
  }

  function toggleSelect(rowKey) {
    setSelectedKeys((prev) => {
      const next = new Set(prev);
      if (next.has(rowKey)) next.delete(rowKey);
      else next.add(rowKey);
      return next;
    });
  }

  function onEditValue(rowKey, newValue) {
    setEditedValues((prev) => {
      const next = new Map(prev);
      next.set(rowKey, newValue);
      return next;
    });
  }

  function getDisplayedValue(row) {
    const k = makeRowKey(row);
    if (editedValues.has(k)) return editedValues.get(k);
    return row.configValue ?? "";
  }

  async function doSave() {
    setMessage("");

    // Build updates array ONLY for selected rows and only if value changed
    const updates = [];
    selectedKeys.forEach((k) => {
      const [cat, name] = k.split("|||");
      const original = originalValues.get(k);
      const current = editedValues.has(k) ? editedValues.get(k) : original;

      if (current !== undefined && current !== original) {
        updates.push({
          category: cat,
          configName: name,
          configValue: current,
        });
      }
    });

    if (updates.length === 0) {
      setMessage("No changes to save");
      return;
    }

    try {
      await api.save({ updates, userid });
      setMessage("Saved successfully");

      // Refresh list after save
      await doSearch();
    } catch (e) {
      setMessage(e.message || "Save failed");
    }
  }

  async function doExcelDownload() {
    setMessage("");
    try {
      await api.downloadExcel({ category, configName, userid });
      setMessage("Excel download started");
    } catch (e) {
      setMessage(e.message || "Excel download failed");
    }
  }

  async function openHistory(row) {
    const cat = row.category || "";
    const name = row.configName || "";
    setHistoryTitle(`${cat} / ${name}`);
    setHistoryOpen(true);
    setHistoryLoading(true);
    setHistoryRows([]);

    try {
      const resp = await api.history({ category: cat, configName: name, userid });
      const list = normalizeHistoryResponse(resp);
      setHistoryRows(list);
    } catch (e) {
      setHistoryRows([]);
      setMessage(e.message || "History load failed");
    } finally {
      setHistoryLoading(false);
    }
  }

  async function submitCreate() {
    setMessage("");

    if (!createCategory || !createKeyName || !createKeyValue) {
      setMessage("Please fill Category, Key Name, and Key Value");
      return;
    }

    try {
      await api.create({
        category: createCategory,
        configName: createKeyName,
        configValue: createKeyValue,
        userid,
      });

      setMessage("Created successfully");
      setShowCreate(false);
      setCreateCategory("");
      setCreateKeyName("");
      setCreateKeyValue("");

      await doSearch();
    } catch (e) {
      setMessage(e.message || "Create failed");
    }
  }

  useEffect(() => {
    // optional: auto-search on load if you want
    // doSearch();
  }, []);

  return (
    <div>
      <h2>Admin System Configuration</h2>

      <div>
        <div>
          <label>
            Key Category{" "}
            <input
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") doSearch();
              }}
            />
          </label>

          <label style={{ marginLeft: 12 }}>
            Key Name{" "}
            <input
              value={configName}
              onChange={(e) => setConfigName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") doSearch();
              }}
            />
          </label>

          <button style={{ marginLeft: 12 }} onClick={doSearch} disabled={loading}>
            {loading ? "Searching..." : "Search"}
          </button>

          <button style={{ marginLeft: 12 }} onClick={() => setShowCreate((s) => !s)}>
            + Create
          </button>
        </div>
      </div>

      {showCreate && (
        <div style={{ marginTop: 16 }}>
          <h3>Create Admin System Configuration</h3>

          <label>
            Key Category*{" "}
            <input value={createCategory} onChange={(e) => setCreateCategory(e.target.value)} />
          </label>

          <label style={{ marginLeft: 12 }}>
            Key Name*{" "}
            <input value={createKeyName} onChange={(e) => setCreateKeyName(e.target.value)} />
          </label>

          <label style={{ marginLeft: 12 }}>
            Key Value*{" "}
            <input value={createKeyValue} onChange={(e) => setCreateKeyValue(e.target.value)} />
          </label>

          <button style={{ marginLeft: 12 }} onClick={submitCreate}>
            Submit
          </button>
        </div>
      )}

      <div style={{ marginTop: 16 }}>
        <h3>Search Results: {totalCount}</h3>

        <div style={{ marginBottom: 8 }}>
          <button onClick={() => window.print()} disabled={totalCount === 0}>
            Print
          </button>

          <button onClick={doExcelDownload} disabled={totalCount === 0} style={{ marginLeft: 8 }}>
            Excel
          </button>

          <button onClick={doSave} disabled={totalCount === 0} style={{ marginLeft: 8 }}>
            Save
          </button>

          <label style={{ marginLeft: 16 }}>
            Page size{" "}
            <select
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value));
                setPage(1);
              }}
            >
              <option value={10}>10</option>
              <option value={25}>25</option>
              <option value={50}>50</option>
              <option value={100}>100</option>
            </select>
          </label>
        </div>

        <table border="1" cellPadding="6" cellSpacing="0">
          <thead>
            <tr>
              <th></th>
              <th>Key Category</th>
              <th>Key Name</th>
              <th>Key Value</th>
              <th>History</th>
            </tr>
          </thead>

          <tbody>
            {pageRows.length === 0 ? (
              <tr>
                <td colSpan="5">No data available in table</td>
              </tr>
            ) : (
              pageRows.map((r, idx) => {
                const rowKey = makeRowKey(r);
                return (
                  <tr key={`${rowKey}-${idx}`}>
                    <td>
                      <input
                        type="checkbox"
                        checked={selectedKeys.has(rowKey)}
                        onChange={() => toggleSelect(rowKey)}
                      />
                    </td>
                    <td>{r.category}</td>
                    <td>{r.configName}</td>
                    <td>
                      <input
                        value={getDisplayedValue(r)}
                        onChange={(e) => onEditValue(rowKey, e.target.value)}
                      />
                    </td>
                    <td>
                      <button type="button" onClick={() => openHistory(r)}>
                        Click
                      </button>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>

        <div style={{ marginTop: 8 }}>
          <button onClick={() => setPage(1)} disabled={page === 1}>
            First
          </button>
          <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page === 1} style={{ marginLeft: 6 }}>
            Previous
          </button>

          <span style={{ marginLeft: 10, marginRight: 10 }}>
            Page {page} of {totalPages}
          </span>

          <button
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
            disabled={page === totalPages}
          >
            Next
          </button>
          <button onClick={() => setPage(totalPages)} disabled={page === totalPages} style={{ marginLeft: 6 }}>
            Last
          </button>
        </div>
      </div>

      {message && (
        <div style={{ marginTop: 12 }}>
          <strong>{message}</strong>
        </div>
      )}

      {historyOpen && (
        <div
          role="dialog"
          aria-modal="true"
          style={{
            position: "fixed",
            top: 40,
            left: 40,
            right: 40,
            bottom: 40,
            background: "white",
            border: "1px solid black",
            padding: 16,
            overflow: "auto",
          }}
        >
          <div>
            <h3>Admin System History</h3>
            <div>{historyTitle}</div>
            <button style={{ float: "right" }} onClick={() => setHistoryOpen(false)}>
              X
            </button>
          </div>

          {historyLoading ? (
            <div>Loading...</div>
          ) : (
            <table border="1" cellPadding="6" cellSpacing="0" style={{ marginTop: 12 }}>
              <thead>
                <tr>
                  <th>Key Category</th>
                  <th>Key Name</th>
                  <th>Key Value</th>
                  <th>Action</th>
                  <th>Updated Date</th>
                  <th>Updated By</th>
                </tr>
              </thead>
              <tbody>
                {historyRows.length === 0 ? (
                  <tr>
                    <td colSpan="6">No history</td>
                  </tr>
                ) : (
                  historyRows.map((h, i) => (
                    <tr key={i}>
                      <td>{h.category || h.keyCategory}</td>
                      <td>{h.configName || h.keyName}</td>
                      <td>{h.configValue || h.keyValue}</td>
                      <td>{h.action}</td>
                      <td>{h.updatedDate}</td>
                      <td>{h.updatedBy}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}
// src/App.js
import React from "react";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import AdminSystemConfig from "./pages/AdminSystemConfig";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/admin-system-config" element={<AdminSystemConfig />} />
      </Routes>
    </BrowserRouter>
  );
}


// src/lib/config.js
const config = {
  API_BASE_URL: "", // e.g. "https://your-domain.com/api" or "" if same origin proxy
};

export default config;


