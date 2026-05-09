<template>
  <div class="spawn-editor">
    <header class="hero">
      <div>
        <p class="eyebrow">World Editor</p>
        <h1>NPC Spawn Editor</h1>
        <p class="lede">
          Edit JSON-backed NPC spawns live, use an online dev as your placement cursor, then save when the world looks right.
        </p>
      </div>
      <div class="status-card" :class="{ dirty }">
        <span>{{ dirty ? 'Unsaved changes' : 'Clean' }}</span>
        <strong>{{ entries.length }}</strong>
        <small>managed spawns</small>
      </div>
    </header>

    <section v-if="error" class="notice error">{{ error }}</section>
    <section v-if="status" class="notice">{{ status }}</section>

    <section class="toolbar">
      <label>
        Placement player
        <select v-model="selectedPlayer" @focus="fetchDevPlayers">
          <option value="" disabled>Select online owner/dev</option>
          <option v-for="player in devPlayers" :key="player.username" :value="player.username">
            {{ player.username }} @ {{ player.tile.x }},{{ player.tile.z }},{{ player.tile.height }}
          </option>
        </select>
      </label>
      <button @click="fetchAll" :disabled="loading">Refresh</button>
      <button @click="saveSpawns" :disabled="loading || !dirty">Save to disk</button>
      <button class="ghost" @click="reloadSpawns" :disabled="loading">Reload from disk</button>
    </section>

    <main class="workspace">
      <aside class="panel list-panel">
        <div class="panel-title">
          <h2>Spawns</h2>
          <input v-model="filter" type="search" placeholder="Filter name, id, tile, tag..." />
        </div>

        <div class="spawn-list">
          <button
            v-for="entry in filteredEntries"
            :key="entry.key"
            class="spawn-row"
            :class="{ selected: selectedKey === entry.key, disabled: !entry.enabled }"
            @click="selectEntry(entry.key)"
          >
            <span>
              <strong>{{ entry.name }}</strong>
              <small>{{ entry.key }}</small>
            </span>
            <em>{{ entry.x }},{{ entry.z }},{{ entry.height }}</em>
          </button>
        </div>
      </aside>

      <section class="panel editor-panel">
        <div class="panel-title">
          <h2>{{ draft.key ? 'Selected Spawn' : 'Create Spawn' }}</h2>
          <span v-if="configPath" class="path">{{ configPath }}</span>
        </div>

        <div class="search-box">
          <input
            v-model="npcQuery"
            type="search"
            placeholder="Search NPCs by name..."
            @keyup.enter="searchNpcs"
          />
          <button @click="searchNpcs">Search NPCs</button>
        </div>

        <div v-if="npcResults.length" class="results">
          <button v-for="npc in npcResults" :key="npc.id" @click="createSpawn(npc.id)">
            Create {{ npc.name }} ({{ npc.id }}) at {{ selectedPlayer || 'selected player' }}
          </button>
        </div>

        <form v-if="draft.key" class="edit-grid" @submit.prevent="updateSpawn">
          <label>
            NPC id
            <input v-model.number="draft.npcId" type="number" min="0" />
          </label>
          <label>
            Name
            <input :value="draft.name" type="text" disabled />
          </label>
          <label>
            X
            <input v-model.number="draft.x" type="number" />
          </label>
          <label>
            Z
            <input v-model.number="draft.z" type="number" />
          </label>
          <label>
            Height
            <input v-model.number="draft.height" type="number" min="0" max="3" />
          </label>
          <label>
            Facing
            <select v-model="draft.facing">
              <option v-for="facing in facings" :key="facing" :value="facing">{{ facing }}</option>
            </select>
          </label>
          <label>
            Walk radius
            <input v-model.number="draft.walkRadius" type="number" min="0" />
          </label>
          <label>
            Tags
            <input v-model="draft.tagsText" type="text" placeholder="yanille, slayer, shop" />
          </label>
          <label class="wide">
            Notes
            <textarea v-model="draft.notes" rows="3" placeholder="Optional notes for future builders"></textarea>
          </label>

          <div class="toggles">
            <label><input v-model="draft.enabled" type="checkbox" /> Enabled</label>
            <label><input v-model="draft.active" type="checkbox" /> Active AI</label>
          </div>

          <div class="nudge">
            <button type="button" @click="nudge(0, 1, 0)">North +1</button>
            <button type="button" @click="nudge(1, 0, 0)">East +1</button>
            <button type="button" @click="nudge(0, -1, 0)">South -1</button>
            <button type="button" @click="nudge(-1, 0, 0)">West -1</button>
            <button type="button" @click="nudge(0, 0, 1)">Height +1</button>
            <button type="button" @click="nudge(0, 0, -1)">Height -1</button>
          </div>

          <div class="actions">
            <button type="submit">Apply changes</button>
            <button type="button" @click="moveToPlayer">Move to player tile</button>
            <button type="button" @click="duplicateSpawn">Duplicate at player tile</button>
            <button type="button" class="danger" @click="deleteSpawn">Delete</button>
          </div>
        </form>

        <div v-else class="empty-state">
          <h3>No spawn selected</h3>
          <p>Select a spawn from the left, or search an NPC above to create one at the selected player's tile.</p>
        </div>
      </section>
    </main>
  </div>
</template>

<script>
import axios from 'axios';

const API = 'http://127.0.0.1:4567';
const EMPTY_DRAFT = {
  key: '',
  npcId: -1,
  name: '',
  x: 0,
  z: 0,
  height: 0,
  walkRadius: 0,
  facing: 'SOUTH',
  active: true,
  enabled: true,
  tagsText: '',
  notes: ''
};

export default {
  name: 'NpcSpawnsView',
  data() {
    return {
      loading: false,
      dirty: false,
      configPath: '',
      entries: [],
      devPlayers: [],
      selectedPlayer: '',
      selectedKey: '',
      draft: { ...EMPTY_DRAFT },
      filter: '',
      npcQuery: '',
      npcResults: [],
      status: '',
      error: '',
      facings: ['NORTH', 'NORTH_EAST', 'EAST', 'SOUTH_EAST', 'SOUTH', 'SOUTH_WEST', 'WEST', 'NORTH_WEST']
    };
  },
  computed: {
    filteredEntries() {
      const q = this.filter.trim().toLowerCase();
      if (!q) {
        return this.entries;
      }
      return this.entries.filter(entry => {
        return [
          entry.key,
          entry.name,
          String(entry.npcId),
          `${entry.x},${entry.z},${entry.height}`,
          entry.facing,
          (entry.tags || []).join(','),
          entry.notes || ''
        ].some(value => value.toLowerCase().includes(q));
      });
    }
  },
  created() {
    this.fetchAll();
  },
  methods: {
    async fetchAll() {
      this.loading = true;
      this.error = '';
      try {
        const [spawns, players] = await Promise.all([
          axios.get(`${API}/world-editor/npc-spawns`),
          axios.get(`${API}/world-editor/dev-players`)
        ]);
        this.ingestSpawnState(spawns.data);
        this.devPlayers = players.data.players || [];
        if (!this.selectedPlayer && this.devPlayers.length) {
          this.selectedPlayer = this.devPlayers[0].username;
        }
      } catch (err) {
        this.error = this.errorMessage(err);
      } finally {
        this.loading = false;
      }
    },
    async fetchDevPlayers() {
      try {
        const res = await axios.get(`${API}/world-editor/dev-players`);
        this.devPlayers = res.data.players || [];
      } catch (err) {
        this.error = this.errorMessage(err);
      }
    },
    async searchNpcs() {
      if (!this.npcQuery.trim()) {
        this.npcResults = [];
        return;
      }
      this.error = '';
      try {
        const res = await axios.get(`${API}/world-editor/npcs/search`, { params: { q: this.npcQuery } });
        this.npcResults = res.data.results || [];
        this.status = `Found ${this.npcResults.length} NPC matches.`;
      } catch (err) {
        this.error = this.errorMessage(err);
      }
    },
    async createSpawn(npcId) {
      if (!this.requirePlacementPlayer()) {
        return;
      }
      await this.mutate(() => axios.post(`${API}/world-editor/npc-spawns`, {
        npcId,
        player: this.selectedPlayer
      }), 'Spawn created.');
      this.npcResults = [];
    },
    async updateSpawn() {
      if (!this.draft.key) {
        return;
      }
      await this.mutate(() => axios.patch(`${API}/world-editor/npc-spawns/${encodeURIComponent(this.draft.key)}`, this.payloadFromDraft()), 'Spawn updated.');
    },
    async moveToPlayer() {
      if (!this.requireSelection() || !this.requirePlacementPlayer()) {
        return;
      }
      await this.mutate(() => axios.post(`${API}/world-editor/npc-spawns/${encodeURIComponent(this.draft.key)}/move-to-player`, {
        player: this.selectedPlayer
      }), 'Spawn moved to player tile.');
    },
    async duplicateSpawn() {
      if (!this.requireSelection() || !this.requirePlacementPlayer()) {
        return;
      }
      await this.mutate(() => axios.post(`${API}/world-editor/npc-spawns/${encodeURIComponent(this.draft.key)}/duplicate`, {
        player: this.selectedPlayer
      }), 'Spawn duplicated.');
    },
    async deleteSpawn() {
      if (!this.requireSelection()) {
        return;
      }
      if (!window.confirm(`Delete ${this.draft.name}?`)) {
        return;
      }
      await this.mutate(() => axios.delete(`${API}/world-editor/npc-spawns/${encodeURIComponent(this.draft.key)}`), 'Spawn deleted.');
    },
    async saveSpawns() {
      await this.mutate(() => axios.post(`${API}/world-editor/npc-spawns/save`), 'Spawns saved to disk.');
    },
    async reloadSpawns() {
      if (this.dirty && !window.confirm('Discard unsaved spawn edits and reload from disk?')) {
        return;
      }
      await this.mutate(() => axios.post(`${API}/world-editor/npc-spawns/reload`), 'Spawns reloaded from disk.');
    },
    async nudge(dx, dz, dh) {
      this.draft.x += dx;
      this.draft.z += dz;
      this.draft.height = Math.max(0, Math.min(3, this.draft.height + dh));
      await this.updateSpawn();
    },
    async mutate(request, success) {
      this.loading = true;
      this.error = '';
      try {
        const res = await request();
        this.ingestSpawnState(res.data);
        this.status = success;
      } catch (err) {
        this.error = this.errorMessage(err);
      } finally {
        this.loading = false;
      }
    },
    selectEntry(key) {
      this.selectedKey = key;
      const entry = this.entries.find(item => item.key === key);
      this.syncDraft(entry);
    },
    ingestSpawnState(data) {
      this.entries = data.entries || [];
      this.dirty = Boolean(data.dirty);
      this.configPath = data.configPath || this.configPath;

      const preferredKey = data.entry?.key || this.selectedKey;
      const preferred = this.entries.find(entry => entry.key === preferredKey) || this.entries[0];
      if (preferred) {
        this.selectEntry(preferred.key);
      } else {
        this.selectedKey = '';
        this.syncDraft(null);
      }
    },
    syncDraft(entry) {
      if (!entry) {
        this.draft = { ...EMPTY_DRAFT };
        return;
      }
      this.draft = {
        ...entry,
        tagsText: (entry.tags || []).join(', '),
        notes: entry.notes || ''
      };
    },
    payloadFromDraft() {
      return {
        npcId: Number(this.draft.npcId),
        x: Number(this.draft.x),
        z: Number(this.draft.z),
        height: Number(this.draft.height),
        walkRadius: Number(this.draft.walkRadius),
        facing: this.draft.facing,
        active: Boolean(this.draft.active),
        enabled: Boolean(this.draft.enabled),
        tags: this.draft.tagsText.split(',').map(tag => tag.trim()).filter(Boolean),
        notes: this.draft.notes
      };
    },
    requireSelection() {
      if (!this.draft.key) {
        this.error = 'Select a spawn first.';
        return false;
      }
      return true;
    },
    requirePlacementPlayer() {
      if (!this.selectedPlayer) {
        this.error = 'Select an online owner/dev player first.';
        return false;
      }
      return true;
    },
    errorMessage(err) {
      return err.response?.data?.error || err.message || 'Unexpected editor error.';
    }
  }
};
</script>

<style lang="scss" scoped>
.spawn-editor {
  min-height: 100vh;
  padding: 34px;
  background:
    radial-gradient(circle at top left, rgba(168, 114, 58, 0.22), transparent 30%),
    linear-gradient(135deg, #efe2c7 0%, #c7d8cf 48%, #889d90 100%);
  color: #17211b;
}

.hero,
.toolbar,
.workspace {
  max-width: 1420px;
  margin: 0 auto 22px;
}

.hero,
.toolbar,
.panel,
.notice {
  border: 1px solid rgba(35, 46, 38, 0.18);
  box-shadow: 0 22px 60px rgba(24, 35, 29, 0.14);
}

.hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 30px;
  border-radius: 28px;
  background: rgba(255, 249, 235, 0.82);
}

.eyebrow {
  color: #8d501d;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

h1,
h2,
h3 {
  margin: 0;
  line-height: 1.05;
}

h1 {
  margin-top: 8px;
  font-size: clamp(34px, 5vw, 64px);
}

.lede {
  max-width: 760px;
  margin-top: 14px;
  line-height: 1.45;
  color: #435044;
}

.status-card {
  min-width: 170px;
  padding: 20px;
  border-radius: 22px;
  background: #1f2b23;
  color: #f8edd4;
  text-align: center;
}

.status-card.dirty {
  background: #a74d24;
}

.status-card span,
.status-card small {
  display: block;
}

.status-card strong {
  display: block;
  margin: 10px 0;
  font-size: 46px;
}

.notice,
.toolbar,
.panel {
  border-radius: 22px;
  background: rgba(255, 253, 245, 0.88);
}

.notice {
  max-width: 1420px;
  margin: 0 auto 14px;
  padding: 14px 18px;
}

.notice.error {
  background: #ffe6dc;
  color: #8e260e;
}

.toolbar {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  padding: 18px;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(320px, 420px) 1fr;
  gap: 22px;
}

.panel {
  padding: 20px;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.path {
  max-width: 420px;
  overflow: hidden;
  color: #6e786f;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

label {
  display: grid;
  gap: 7px;
  color: #566157;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

input,
select,
textarea {
  width: 100%;
  border: 1px solid #bec7bb;
  border-radius: 12px;
  background: #fffdf7;
  color: #17211b;
  font: inherit;
  padding: 11px 12px;
}

button {
  border: 0;
  border-radius: 13px;
  background: #26372d;
  color: #fff9eb;
  cursor: pointer;
  font-weight: 800;
  padding: 12px 15px;
}

button:hover {
  transform: translateY(-1px);
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.48;
  transform: none;
}

button.ghost {
  background: #d8cab0;
  color: #2d332d;
}

button.danger {
  background: #9f3117;
}

.spawn-list {
  display: grid;
  gap: 10px;
  max-height: 680px;
  overflow: auto;
}

.spawn-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid transparent;
  background: #f6eddb;
  color: #26372d;
  text-align: left;
}

.spawn-row.selected {
  border-color: #26372d;
  background: #26372d;
  color: #fff9eb;
}

.spawn-row.disabled {
  opacity: 0.6;
}

.spawn-row small,
.spawn-row em {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  font-style: normal;
  opacity: 0.75;
}

.search-box,
.results,
.actions,
.nudge,
.toggles {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.search-box {
  margin-bottom: 14px;
}

.search-box input {
  flex: 1;
  min-width: 240px;
}

.results {
  margin-bottom: 20px;
}

.results button {
  background: #81603a;
}

.edit-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(160px, 1fr));
  gap: 14px;
}

.wide,
.toggles,
.nudge,
.actions {
  grid-column: 1 / -1;
}

.toggles label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toggles input {
  width: auto;
}

.empty-state {
  display: grid;
  min-height: 320px;
  place-content: center;
  text-align: center;
}

.empty-state p {
  max-width: 460px;
  margin-top: 10px;
  color: #5c685e;
  line-height: 1.45;
}

@media (max-width: 1050px) {
  .hero,
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .workspace {
    grid-template-columns: 1fr;
  }

  .edit-grid {
    grid-template-columns: 1fr;
  }
}
</style>
