<template>
  <div class="spawn-editor">
    <header class="hero">
      <div>
        <p class="eyebrow">World Editor</p>
        <h1>NPC Spawn Editor</h1>
        <p class="lede">Place JSON-backed NPCs, tune their reusable mechanics, attach drops and shops, then save when the world looks right.</p>
      </div>
      <div class="status-card" :class="{ dirty: dirty || definitionsDirty || shopsDirty }">
        <span>{{ dirtyLabel }}</span>
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
      <button @click="saveSpawns" :disabled="loading || !dirty">Save spawns</button>
      <button class="ghost" @click="reloadSpawns" :disabled="loading">Reload spawns</button>
      <button @click="saveDefinitions" :disabled="definitionLoading || (!definitionsDirty && !shopsDirty)">Save definitions</button>
      <button class="ghost" @click="reloadDefinitions" :disabled="definitionLoading">Reload definitions</button>
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
          <h2>{{ draft.key ? draft.name : 'Create Spawn' }}</h2>
          <span v-if="configPath" class="path">{{ configPath }}</span>
        </div>

        <nav class="tabs" aria-label="NPC editor sections">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            class="tab"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </nav>

        <section v-if="activeTab === 'spawn'">
          <div class="search-box">
            <input v-model="npcQuery" type="search" placeholder="Search NPCs by name..." @keyup.enter="searchNpcs" />
            <button @click="searchNpcs">Search NPCs</button>
          </div>

          <div v-if="npcResults.length" class="results">
            <button v-for="npc in npcResults" :key="npc.id" @click="createSpawn(npc.id)">
              <img v-if="npc.imageUrl" :src="npc.imageUrl" alt="" />
              Create {{ npc.name }} ({{ npc.id }}) at {{ selectedPlayer || 'selected player' }}
            </button>
          </div>

          <form v-if="draft.key" class="edit-grid" @submit.prevent="updateSpawn">
            <label>NPC id <input v-model.number="draft.npcId" type="number" min="0" /></label>
            <label>Name <input :value="draft.name" type="text" disabled /></label>
            <label>X <input v-model.number="draft.x" type="number" /></label>
            <label>Z <input v-model.number="draft.z" type="number" /></label>
            <label>Height <input v-model.number="draft.height" type="number" min="0" max="3" /></label>
            <label>
              Facing
              <select v-model="draft.facing">
                <option v-for="facing in facings" :key="facing" :value="facing">{{ facing }}</option>
              </select>
            </label>
            <label>Walk radius <input v-model.number="draft.walkRadius" type="number" min="0" /></label>
            <label>
              Aggressive override
              <select v-model="draft.aggressiveText">
                <option value="">Use definition</option>
                <option value="true">Aggressive</option>
                <option value="false">Not aggressive</option>
              </select>
            </label>
            <label>Aggression radius override <input v-model="draft.aggressionRadiusText" type="number" min="0" placeholder="definition" /></label>
            <label>Follow range override <input v-model="draft.followRangeText" type="number" min="0" placeholder="definition" /></label>
            <label>Shop override <input v-model="draft.shopKey" type="text" placeholder="Use definition/default" /></label>
            <label>Tags <input v-model="draft.tagsText" type="text" placeholder="yanille, slayer, shop" /></label>
            <label class="wide">Notes <textarea v-model="draft.notes" rows="3"></textarea></label>

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
              <button type="submit">Apply spawn</button>
              <button type="button" @click="moveToPlayer">Move to player tile</button>
              <button type="button" @click="duplicateSpawn">Duplicate at player tile</button>
              <button type="button" class="danger" @click="deleteSpawn">Delete</button>
            </div>
          </form>
        </section>

        <section v-else-if="draft.key" class="definition-shell">
          <div class="definition-banner" :class="{ dirty: definitionsDirty || shopsDirty }">
            <div>
              <strong>{{ definitionDraft.name || draft.name }}</strong>
              <small>NPC {{ currentNpcId }}</small>
            </div>
            <div class="definition-actions">
              <button type="button" @click="fetchNpcDefinition(currentNpcId)" :disabled="definitionLoading">Refresh</button>
              <button type="button" @click="applyCurrentDefinition" :disabled="definitionLoading">Apply tab</button>
            </div>
          </div>

          <form v-if="definitionLoaded" class="edit-grid" @submit.prevent="applyCurrentDefinition">
            <template v-if="activeTab === 'definition'">
              <label>Name <input v-model="definitionDraft.name" type="text" /></label>
              <label>Follow range <input v-model.number="definitionDraft.followRange" type="number" min="0" /></label>
              <label>Shop key <input v-model="definitionDraft.shopKey" type="text" placeholder="yanille_tools" /></label>
              <label>Tags <input v-model="definitionDraft.tagsText" type="text" placeholder="rat, shop, slayer" /></label>
              <label class="wide">Notes <textarea v-model="definitionDraft.notes" rows="3"></textarea></label>
            </template>

            <template v-if="activeTab === 'drops'">
              <label class="wide">Always drops JSON <textarea v-model="dropText.always" rows="6"></textarea></label>
              <label class="wide">Main drops JSON <textarea v-model="dropText.main" rows="6"></textarea></label>
              <label class="wide">Preroll drops JSON <textarea v-model="dropText.preroll" rows="6"></textarea></label>
              <label class="wide">Tertiary drops JSON <textarea v-model="dropText.tertiary" rows="6"></textarea></label>
            </template>

            <template v-if="activeTab === 'aggression'">
              <div class="toggles wide">
                <label><input v-model="definitionDraft.aggression.aggressive" type="checkbox" /> Aggressive</label>
                <label><input v-model="definitionDraft.aggression.alwaysAggressive" type="checkbox" /> Always aggressive</label>
                <label><input v-model="definitionDraft.aggression.retaliates" type="checkbox" /> Retaliates</label>
              </div>
              <label>Radius <input v-model.number="definitionDraft.aggression.radius" type="number" min="0" /></label>
              <label>Search delay <input v-model.number="definitionDraft.aggression.searchDelay" type="number" min="1" /></label>
              <label>Tolerance ticks <input v-model="definitionDraft.aggression.toleranceTicksText" type="number" min="0" placeholder="default" /></label>
            </template>

            <template v-if="activeTab === 'combat'">
              <label>Attack <input v-model.number="definitionDraft.combat.stats.attack" type="number" min="0" /></label>
              <label>Strength <input v-model.number="definitionDraft.combat.stats.strength" type="number" min="0" /></label>
              <label>Defence <input v-model.number="definitionDraft.combat.stats.defence" type="number" min="0" /></label>
              <label>Ranged <input v-model.number="definitionDraft.combat.stats.ranged" type="number" min="0" /></label>
              <label>Magic <input v-model.number="definitionDraft.combat.stats.magic" type="number" min="0" /></label>
              <label>Hitpoints <input v-model.number="definitionDraft.combat.stats.hitpoints" type="number" min="0" /></label>
              <label>Attack speed <input v-model="definitionDraft.combat.attackSpeedText" type="number" min="1" placeholder="default" /></label>
              <label>Respawn delay <input v-model="definitionDraft.combat.respawnDelayText" type="number" min="0" placeholder="default" /></label>
              <label>Attack anim <input v-model="definitionDraft.combat.animations.attackText" type="number" placeholder="default" /></label>
              <label>Block anim <input v-model="definitionDraft.combat.animations.blockText" type="number" placeholder="default" /></label>
              <label>Death anim <input v-model="definitionDraft.combat.animations.deathText" type="number" placeholder="default" /></label>
            </template>

            <template v-if="activeTab === 'shop'">
              <label>Shop key <input v-model="shopDraft.id" type="text" placeholder="yanille_tools" /></label>
              <label>Display name <input v-model="shopDraft.name" type="text" /></label>
              <label>Currency item <input v-model.number="shopDraft.currencyItemId" type="number" min="0" /></label>
              <label>Restock ticks <input v-model.number="shopDraft.restockTicks" type="number" min="1" /></label>
              <label>NPC ids <input v-model="shopDraft.npcIdsText" type="text" placeholder="123, 456" /></label>
              <div class="toggles wide">
                <label><input v-model="shopDraft.buysItems" type="checkbox" /> Buys items</label>
                <label><input v-model="shopDraft.sellsItems" type="checkbox" /> Sells items</label>
              </div>
              <label class="wide">Shop items JSON <textarea v-model="shopDraft.itemsText" rows="10"></textarea></label>
              <label class="wide">Shop notes <textarea v-model="shopDraft.notes" rows="3"></textarea></label>
            </template>

            <template v-if="activeTab === 'image'">
              <div class="image-preview">
                <img :src="npcImageUrl" :alt="`${definitionDraft.name || draft.name} preview`" />
              </div>
              <label class="wide">Wiki image URL <input v-model="definitionDraft.imageUrl" type="url" placeholder="https://oldschool.runescape.wiki/images/..." /></label>
            </template>

            <div class="actions">
              <button type="submit" :disabled="definitionLoading">Apply tab</button>
            </div>
          </form>
        </section>

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
  aggressiveText: '',
  aggressionRadiusText: '',
  followRangeText: '',
  shopKey: '',
  tagsText: '',
  notes: ''
};

function emptyDefinition() {
  return {
    id: -1,
    name: '',
    imageUrl: '',
    shopKey: '',
    tagsText: '',
    notes: '',
    followRange: 16,
    aggression: {
      aggressive: false,
      radius: 0,
      searchDelay: 5,
      toleranceTicksText: '',
      alwaysAggressive: false,
      retaliates: true
    },
    combat: {
      stats: { attack: 0, strength: 0, defence: 0, ranged: 0, magic: 0, hitpoints: 0 },
      bonuses: {},
      animations: { attackText: '', blockText: '', deathText: '' },
      sounds: {},
      attackSpeedText: '',
      respawnDelayText: '',
      slayerReq: null,
      slayerXp: null,
      poisonChance: null,
      venomChance: null,
      immunePoison: null,
      immuneVenom: null,
      immuneCannons: null,
      immuneThralls: null
    }
  };
}

function emptyShop() {
  return {
    id: '',
    name: '',
    npcIdsText: '',
    currencyItemId: 995,
    buysItems: true,
    sellsItems: true,
    restockTicks: 100,
    tags: [],
    notes: '',
    itemsText: '[]'
  };
}

export default {
  name: 'NpcSpawnsView',
  data() {
    return {
      loading: false,
      dirty: false,
      definitionsDirty: false,
      shopsDirty: false,
      configPath: '',
      entries: [],
      definitions: [],
      shops: [],
      devPlayers: [],
      selectedPlayer: '',
      selectedKey: '',
      draft: { ...EMPTY_DRAFT },
      definitionDraft: emptyDefinition(),
      shopDraft: emptyShop(),
      dropText: { always: '[]', main: '[]', preroll: '[]', tertiary: '[]' },
      definitionLoaded: false,
      definitionLoading: false,
      filter: '',
      npcQuery: '',
      npcResults: [],
      npcImageUrl: '',
      activeTab: 'spawn',
      status: '',
      error: '',
      tabs: [
        { key: 'spawn', label: 'Spawn' },
        { key: 'definition', label: 'NPC Definition' },
        { key: 'drops', label: 'Drops' },
        { key: 'aggression', label: 'Aggression' },
        { key: 'combat', label: 'Combat' },
        { key: 'shop', label: 'Shop' },
        { key: 'image', label: 'Image' }
      ],
      facings: ['NORTH', 'NORTH_EAST', 'EAST', 'SOUTH_EAST', 'SOUTH', 'SOUTH_WEST', 'WEST', 'NORTH_WEST']
    };
  },
  computed: {
    currentNpcId() {
      return Number(this.draft.npcId);
    },
    dirtyLabel() {
      if (this.dirty || this.definitionsDirty || this.shopsDirty) {
        return 'Unsaved changes';
      }
      return 'Clean';
    },
    filteredEntries() {
      const q = this.filter.trim().toLowerCase();
      if (!q) {
        return this.entries;
      }
      return this.entries.filter(entry => {
        return [entry.key, entry.name, String(entry.npcId), `${entry.x},${entry.z},${entry.height}`, entry.facing, (entry.tags || []).join(','), entry.notes || '']
          .some(value => value.toLowerCase().includes(q));
      });
    }
  },
  watch: {
    currentNpcId(id) {
      if (id >= 0) {
        this.fetchNpcDefinition(id);
      } else {
        this.resetDefinition();
      }
    },
    activeTab(tab) {
      if (tab === 'shop') {
        this.syncShopDraft();
      }
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
        const [spawns, players, definitions] = await Promise.all([
          axios.get(`${API}/world-editor/npc-spawns`),
          axios.get(`${API}/world-editor/dev-players`),
          axios.get(`${API}/world-editor/npc-definitions`)
        ]);
        this.ingestSpawnState(spawns.data);
        this.ingestDefinitionState(definitions.data);
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
    async fetchNpcDefinition(npcId) {
      if (npcId < 0) {
        this.resetDefinition();
        return;
      }
      this.definitionLoading = true;
      this.error = '';
      try {
        const res = await axios.get(`${API}/world-editor/npc-definitions/${encodeURIComponent(npcId)}`);
        this.ingestDefinition(res.data.definition);
      } catch (err) {
        this.resetDefinition();
        this.error = this.errorMessage(err);
      } finally {
        this.definitionLoading = false;
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
      await this.mutate(() => axios.post(`${API}/world-editor/npc-spawns`, { npcId, player: this.selectedPlayer }), 'Spawn created.');
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
      await this.mutate(() => axios.post(`${API}/world-editor/npc-spawns/${encodeURIComponent(this.draft.key)}/move-to-player`, { player: this.selectedPlayer }), 'Spawn moved.');
    },
    async duplicateSpawn() {
      if (!this.requireSelection() || !this.requirePlacementPlayer()) {
        return;
      }
      await this.mutate(() => axios.post(`${API}/world-editor/npc-spawns/${encodeURIComponent(this.draft.key)}/duplicate`, { player: this.selectedPlayer }), 'Spawn duplicated.');
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
      await this.mutate(() => axios.post(`${API}/world-editor/npc-spawns/save`), 'Spawns saved.');
    },
    async reloadSpawns() {
      if (this.dirty && !window.confirm('Discard unsaved spawn edits and reload from disk?')) {
        return;
      }
      await this.mutate(() => axios.post(`${API}/world-editor/npc-spawns/reload`), 'Spawns reloaded.');
    },
    async applyCurrentDefinition() {
      if (this.activeTab === 'shop') {
        await this.applyShop();
      } else {
        await this.applyDefinition();
      }
    },
    async applyDefinition() {
      const payload = this.payloadFromDefinition();
      if (!payload) {
        return;
      }
      await this.mutateDefinition(() => axios.patch(`${API}/world-editor/npc-definitions/${encodeURIComponent(this.currentNpcId)}`, payload), 'NPC definition applied.');
    },
    async applyShop() {
      const payload = this.payloadFromShop();
      if (!payload) {
        return;
      }
      await this.mutateDefinition(() => axios.patch(`${API}/world-editor/npc-shops/${encodeURIComponent(payload.id)}`, payload), 'NPC shop applied.');
      this.definitionDraft.shopKey = payload.id;
      await this.applyDefinition();
    },
    async saveDefinitions() {
      await this.mutateDefinition(() => axios.post(`${API}/world-editor/npc-definitions/save`), 'NPC definitions saved.');
    },
    async reloadDefinitions() {
      if ((this.definitionsDirty || this.shopsDirty) && !window.confirm('Discard unsaved NPC definition/shop edits and reload from disk?')) {
        return;
      }
      await this.mutateDefinition(() => axios.post(`${API}/world-editor/npc-definitions/reload`), 'NPC definitions reloaded.');
      if (this.currentNpcId >= 0) {
        await this.fetchNpcDefinition(this.currentNpcId);
      }
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
    async mutateDefinition(request, success) {
      this.definitionLoading = true;
      this.error = '';
      try {
        const res = await request();
        this.ingestDefinitionState(res.data);
        if (res.data.definition) {
          this.ingestDefinition(res.data.definition);
        }
        this.status = success;
      } catch (err) {
        this.error = this.errorMessage(err);
      } finally {
        this.definitionLoading = false;
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
    ingestDefinitionState(data) {
      this.definitions = data.definitions || this.definitions;
      this.shops = data.shops || this.shops;
      this.definitionsDirty = Boolean(data.definitionsDirty);
      this.shopsDirty = Boolean(data.shopsDirty);
      this.syncShopDraft();
    },
    syncDraft(entry) {
      if (!entry) {
        this.draft = { ...EMPTY_DRAFT };
        return;
      }
      this.draft = {
        ...entry,
        aggressiveText: entry.aggressive == null ? '' : String(entry.aggressive),
        aggressionRadiusText: entry.aggressionRadius == null ? '' : String(entry.aggressionRadius),
        followRangeText: entry.followRange == null ? '' : String(entry.followRange),
        shopKey: entry.shopKey || '',
        tagsText: (entry.tags || []).join(', '),
        notes: entry.notes || ''
      };
    },
    ingestDefinition(definition) {
      const draft = emptyDefinition();
      Object.assign(draft, definition || {});
      draft.tagsText = (definition.tags || []).join(', ');
      draft.imageUrl = definition.imageUrl || '';
      draft.shopKey = definition.shopKey || '';
      draft.aggression = {
        ...draft.aggression,
        ...(definition.aggression || {}),
        toleranceTicksText: definition.aggression?.toleranceTicks == null ? '' : String(definition.aggression.toleranceTicks)
      };
      draft.combat = {
        ...draft.combat,
        ...(definition.combat || {}),
        stats: { ...draft.combat.stats, ...(definition.combat?.stats || {}) },
        bonuses: { ...(definition.combat?.bonuses || {}) },
        animations: {
          ...(definition.combat?.animations || {}),
          attackText: definition.combat?.animations?.attack == null ? '' : String(definition.combat.animations.attack),
          blockText: definition.combat?.animations?.block == null ? '' : String(definition.combat.animations.block),
          deathText: definition.combat?.animations?.death == null ? '' : String(definition.combat.animations.death)
        },
        sounds: { ...(definition.combat?.sounds || {}) },
        attackSpeedText: definition.combat?.attackSpeed == null ? '' : String(definition.combat.attackSpeed),
        respawnDelayText: definition.combat?.respawnDelay == null ? '' : String(definition.combat.respawnDelay)
      };
      const drops = definition.drops || {};
      this.dropText = {
        always: this.formatJson(drops.always || []),
        main: this.formatJson(drops.main || []),
        preroll: this.formatJson(drops.preroll || []),
        tertiary: this.formatJson(drops.tertiary || [])
      };
      this.definitionDraft = draft;
      this.definitionLoaded = true;
      this.npcImageUrl = `${API}/world-editor/npcs/${encodeURIComponent(this.currentNpcId)}/image?ts=${Date.now()}`;
      this.syncShopDraft();
    },
    syncShopDraft() {
      const key = this.definitionDraft.shopKey || this.draft.shopKey || '';
      const shop = this.shops.find(item => item.id === key);
      if (!shop && key) {
        this.shopDraft = { ...emptyShop(), id: key, name: key, npcIdsText: String(this.currentNpcId) };
        return;
      }
      if (!shop) {
        this.shopDraft = { ...emptyShop(), npcIdsText: this.currentNpcId >= 0 ? String(this.currentNpcId) : '' };
        return;
      }
      this.shopDraft = {
        ...shop,
        npcIdsText: (shop.npcIds || []).join(', '),
        notes: shop.notes || '',
        itemsText: this.formatJson(shop.items || [])
      };
    },
    resetDefinition() {
      this.definitionDraft = emptyDefinition();
      this.shopDraft = emptyShop();
      this.dropText = { always: '[]', main: '[]', preroll: '[]', tertiary: '[]' };
      this.definitionLoaded = false;
      this.npcImageUrl = '';
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
        aggressive: this.draft.aggressiveText === '' ? null : this.draft.aggressiveText === 'true',
        aggressionRadius: this.optionalNumber(this.draft.aggressionRadiusText),
        followRange: this.optionalNumber(this.draft.followRangeText),
        shopKey: this.draft.shopKey || null,
        tags: this.csvList(this.draft.tagsText),
        notes: this.draft.notes
      };
    },
    payloadFromDefinition() {
      const drops = {
        always: this.parseJsonField(this.dropText.always, 'Always drops JSON'),
        main: this.parseJsonField(this.dropText.main, 'Main drops JSON'),
        preroll: this.parseJsonField(this.dropText.preroll, 'Preroll drops JSON'),
        tertiary: this.parseJsonField(this.dropText.tertiary, 'Tertiary drops JSON')
      };
      if (Object.values(drops).some(value => value === null)) {
        return null;
      }
      return {
        id: this.currentNpcId,
        name: this.definitionDraft.name,
        imageUrl: this.definitionDraft.imageUrl || null,
        shopKey: this.definitionDraft.shopKey || null,
        tags: this.csvList(this.definitionDraft.tagsText),
        notes: this.definitionDraft.notes || null,
        followRange: Number(this.definitionDraft.followRange),
        aggression: {
          aggressive: Boolean(this.definitionDraft.aggression.aggressive),
          radius: Number(this.definitionDraft.aggression.radius),
          searchDelay: Number(this.definitionDraft.aggression.searchDelay),
          toleranceTicks: this.optionalNumber(this.definitionDraft.aggression.toleranceTicksText),
          alwaysAggressive: Boolean(this.definitionDraft.aggression.alwaysAggressive),
          retaliates: Boolean(this.definitionDraft.aggression.retaliates)
        },
        combat: {
          ...this.definitionDraft.combat,
          attackSpeed: this.optionalNumber(this.definitionDraft.combat.attackSpeedText),
          respawnDelay: this.optionalNumber(this.definitionDraft.combat.respawnDelayText),
          animations: {
            attack: this.optionalNumber(this.definitionDraft.combat.animations.attackText),
            block: this.optionalNumber(this.definitionDraft.combat.animations.blockText),
            death: this.optionalNumber(this.definitionDraft.combat.animations.deathText)
          }
        },
        drops
      };
    },
    payloadFromShop() {
      const id = this.shopDraft.id.trim();
      if (!id) {
        this.error = 'Shop key is required.';
        return null;
      }
      const items = this.parseJsonField(this.shopDraft.itemsText, 'Shop items JSON');
      if (items === null) {
        return null;
      }
      return {
        id,
        name: this.shopDraft.name || id,
        npcIds: this.csvList(this.shopDraft.npcIdsText).map(Number).filter(value => !Number.isNaN(value)),
        currencyItemId: Number(this.shopDraft.currencyItemId),
        buysItems: Boolean(this.shopDraft.buysItems),
        sellsItems: Boolean(this.shopDraft.sellsItems),
        restockTicks: Number(this.shopDraft.restockTicks),
        tags: this.shopDraft.tags || [],
        notes: this.shopDraft.notes || null,
        items
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
    csvList(text) {
      return String(text || '').split(',').map(item => item.trim()).filter(Boolean);
    },
    optionalNumber(value) {
      if (value === '' || value === null || value === undefined) {
        return null;
      }
      const number = Number(value);
      return Number.isNaN(number) ? null : number;
    },
    formatJson(value) {
      return JSON.stringify(value, null, 2);
    },
    parseJsonField(text, label) {
      try {
        return JSON.parse(text || '[]');
      } catch (err) {
        this.error = `${label} is not valid JSON.`;
        return null;
      }
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
  flex-wrap: wrap;
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

button:disabled {
  cursor: not-allowed;
  opacity: 0.48;
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
.toggles,
.definition-actions {
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
  align-items: center;
  background: #81603a;
  display: flex;
  gap: 10px;
}

.results img {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  object-fit: cover;
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
  padding: 7px;
  border-radius: 18px;
  background: rgba(38, 55, 45, 0.08);
}

.tab {
  background: transparent;
  color: #38463c;
}

.tab.active {
  background: #26372d;
  color: #fff9eb;
}

.definition-shell {
  display: grid;
  gap: 16px;
}

.definition-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px;
  border-radius: 18px;
  background: #f6eddb;
}

.definition-banner.dirty {
  background: #ffe8dd;
}

.definition-banner strong,
.definition-banner small {
  display: block;
}

.definition-banner small {
  margin-top: 4px;
  color: #6e786f;
}

.image-preview {
  display: grid;
  min-height: 280px;
  grid-column: 1 / -1;
  overflow: hidden;
  place-items: center;
  border: 1px dashed rgba(38, 55, 45, 0.3);
  border-radius: 20px;
  background: #fffdf7;
}

.image-preview img {
  max-width: min(100%, 440px);
  max-height: 320px;
  object-fit: contain;
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
