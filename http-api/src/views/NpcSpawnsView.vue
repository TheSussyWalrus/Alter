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
              <div class="drop-builder wide">
                <div class="drop-help">
                  <strong>Drop rows</strong>
                  <span>Use Rare extra for independent 1-in-N rolls. Empty main slots mean the main table can roll no item, leaving only always/rare-extra drops.</span>
                </div>

                <div class="drop-toolbar">
                  <label>
                    Add as
                    <select v-model="newDropTable">
                      <option v-for="table in dropTableOptions" :key="table.key" :value="table.key">{{ table.label }}</option>
                    </select>
                  </label>
                  <label>
                    Empty main slots
                    <input v-model="mainEmptySlotsText" type="number" min="0" placeholder="0" />
                  </label>
                  <label class="search-field">
                    Item search
                    <input v-model="itemQuery" type="search" placeholder="Search item name or id..." @keyup.enter.prevent="searchItems()" />
                  </label>
                  <button type="button" @click="searchItems()" :disabled="definitionLoading">Search items</button>
                  <span v-if="itemSearchLoading" class="inline-status">Searching...</span>
                  <label>
                    Show
                    <select v-model="dropTableFilter">
                      <option value="all">All drops</option>
                      <option v-for="table in dropTableOptions" :key="`filter-${table.key}`" :value="table.key">{{ table.label }}</option>
                    </select>
                  </label>
                  <button type="button" class="ghost" @click="showAdvancedDrops = !showAdvancedDrops">
                    {{ showAdvancedDrops ? 'Hide advanced JSON' : 'Advanced JSON' }}
                  </button>
                </div>

                <div v-if="itemResults.length" class="item-results">
                  <button v-for="item in itemResults" :key="item.id" type="button" @click="addDropFromItem(item)">
                    <strong>{{ item.name }}</strong>
                    <span>{{ item.id }}<template v-if="item.stackable"> - stackable</template><template v-if="item.noted"> - noted</template></span>
                  </button>
                </div>

                <div class="drop-table-wrap">
                  <table class="drop-table">
                    <thead>
                      <tr>
                        <th>Table</th>
                        <th>Item</th>
                        <th>Amount</th>
                        <th>Rarity</th>
                        <th>Preview</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="row in filteredDropRows" :key="row.uid">
                        <td>
                          <select v-model="row.table">
                            <option v-for="table in dropTableOptions" :key="table.key" :value="table.key">{{ table.label }}</option>
                          </select>
                        </td>
                        <td>
                          <div class="item-cell">
                            <input v-model="row.itemIdText" type="number" min="0" placeholder="Item id" @change="lookupDropItem(row)" />
                            <input v-model="row.name" type="text" placeholder="Item name" />
                          </div>
                        </td>
                        <td>
                          <div class="amount-cell">
                            <input v-model="row.minAmountText" type="number" min="1" placeholder="Min" />
                            <span>to</span>
                            <input v-model="row.maxAmountText" type="number" min="1" placeholder="Max" />
                          </div>
                        </td>
                        <td>
                          <input v-if="row.table === 'main'" v-model="row.weightText" type="number" min="1" placeholder="Relative weight" />
                          <input v-else-if="usesOneIn(row.table)" v-model="row.oneInText" type="number" min="1" placeholder="One in..." />
                          <span v-else class="rarity-static">Every kill</span>
                        </td>
                        <td><span class="chance-pill">{{ dropChanceLabel(row) }}</span></td>
                        <td>
                          <div class="row-actions">
                            <button type="button" class="ghost" @click="duplicateDrop(row)">Duplicate</button>
                            <button type="button" class="danger" @click="removeDrop(row)">Delete</button>
                          </div>
                        </td>
                      </tr>
                      <tr v-if="!filteredDropRows.length">
                        <td colspan="6" class="empty-drops">No drops yet. Search an item above and add it to this NPC.</td>
                      </tr>
                      <tr v-if="mainEmptySlots > 0 && (dropTableFilter === 'all' || dropTableFilter === 'main')" class="empty-main-row">
                        <td>Main</td>
                        <td colspan="3">Empty slots</td>
                        <td><span class="chance-pill">{{ emptyMainChanceLabel }}</span></td>
                        <td>
                          <button type="button" class="ghost" @click="mainEmptySlotsText = '0'">Clear</button>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>

                <div v-if="showAdvancedDrops" class="advanced-drops">
                  <div class="advanced-title">
                    <strong>Advanced JSON</strong>
                    <span>Use this for migrations or hand fixes. Edited JSON is applied only after syncing it into rows.</span>
                  </div>
                  <label class="wide">Always drops JSON <textarea v-model="dropText.always" rows="6" @input="advancedDropsDirty = true"></textarea></label>
                  <label class="wide">Main drops JSON <textarea v-model="dropText.main" rows="6" @input="advancedDropsDirty = true"></textarea></label>
                  <label class="wide">Pre-roll drops JSON <textarea v-model="dropText.preroll" rows="6" @input="advancedDropsDirty = true"></textarea></label>
                  <label class="wide">Rare extra drops JSON <textarea v-model="dropText.tertiary" rows="6" @input="advancedDropsDirty = true"></textarea></label>
                  <div class="actions">
                    <button type="button" @click="syncDropRowsFromAdvanced">Sync JSON into rows</button>
                    <button type="button" class="ghost" @click="syncAdvancedDropsFromRows">Refresh JSON from rows</button>
                  </div>
                </div>
              </div>
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
              <div class="drop-builder shop-builder wide">
                <div class="drop-help">
                  <strong>Shop stock</strong>
                  <span>Search items by name or id, add rows, then tune stock, sell price, buy price, and per-item restock.</span>
                </div>

                <div class="drop-toolbar">
                  <label class="search-field">
                    Item search
                    <input v-model="shopItemQuery" type="search" placeholder="Search item name or id..." @keyup.enter.prevent="searchShopItems()" />
                  </label>
                  <button type="button" @click="searchShopItems()" :disabled="definitionLoading">Search items</button>
                  <span v-if="shopItemSearchLoading" class="inline-status">Searching...</span>
                  <button type="button" class="ghost" @click="showAdvancedShopItems = !showAdvancedShopItems">
                    {{ showAdvancedShopItems ? 'Hide advanced JSON' : 'Advanced JSON' }}
                  </button>
                </div>

                <div v-if="shopItemResults.length" class="item-results">
                  <button v-for="item in shopItemResults" :key="`shop-result-${item.id}`" type="button" @click="addShopItemFromItem(item)">
                    <strong>{{ item.name }}</strong>
                    <span>{{ item.id }}<template v-if="item.stackable"> - stackable</template><template v-if="item.noted"> - noted</template></span>
                  </button>
                </div>

                <div class="drop-table-wrap">
                  <table class="drop-table shop-table">
                    <thead>
                      <tr>
                        <th>Item</th>
                        <th>Stock</th>
                        <th>Sell price</th>
                        <th>Buy price</th>
                        <th>Restock</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="row in shopRows" :key="row.uid">
                        <td>
                          <div class="item-cell">
                            <input v-model="row.itemIdText" type="number" min="0" placeholder="Item id" @change="lookupShopItem(row)" />
                            <input v-model="row.name" type="text" placeholder="Item name" />
                          </div>
                        </td>
                        <td><input v-model="row.amountText" type="number" min="0" placeholder="Stock" /></td>
                        <td><input v-model="row.priceText" type="number" min="0" placeholder="0 = default" /></td>
                        <td><input v-model="row.buyPriceText" type="number" min="0" placeholder="default" /></td>
                        <td><input v-model="row.restockTicksText" type="number" min="0" placeholder="shop default" /></td>
                        <td>
                          <div class="row-actions">
                            <button type="button" class="ghost" @click="duplicateShopItem(row)">Duplicate</button>
                            <button type="button" class="danger" @click="removeShopItem(row)">Delete</button>
                          </div>
                        </td>
                      </tr>
                      <tr v-if="!shopRows.length">
                        <td colspan="6" class="empty-drops">No shop stock yet. Search an item above and add it here.</td>
                      </tr>
                    </tbody>
                  </table>
                </div>

                <div v-if="showAdvancedShopItems" class="advanced-drops">
                  <div class="advanced-title">
                    <strong>Advanced JSON</strong>
                    <span>Use this for migrations or hand fixes. Edited JSON is applied only after syncing it into rows.</span>
                  </div>
                  <label class="wide">Shop items JSON <textarea v-model="shopDraft.itemsText" rows="10" @input="advancedShopItemsDirty = true"></textarea></label>
                  <div class="actions">
                    <button type="button" @click="syncShopRowsFromAdvanced">Sync JSON into rows</button>
                    <button type="button" class="ghost" @click="syncAdvancedShopItemsFromRows">Refresh JSON from rows</button>
                  </div>
                </div>
              </div>
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
      dropRows: [],
      shopRows: [],
      dropTableFilter: 'all',
      dropTableOptions: [
        { key: 'always', label: 'Always' },
        { key: 'main', label: 'Main' },
        { key: 'tertiary', label: 'Rare extra' },
        { key: 'preroll', label: 'Pre-roll' }
      ],
      newDropTable: 'tertiary',
      itemQuery: '',
      itemResults: [],
      itemSearchLoading: false,
      itemSearchCache: {},
      itemSearchDebounce: null,
      itemSearchToken: 0,
      shopItemQuery: '',
      shopItemResults: [],
      shopItemSearchLoading: false,
      shopItemSearchDebounce: null,
      shopItemSearchToken: 0,
      showAdvancedDrops: false,
      advancedDropsDirty: false,
      showAdvancedShopItems: false,
      advancedShopItemsDirty: false,
      mainEmptySlotsText: '0',
      nextDropUid: 1,
      nextShopItemUid: 1,
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
    },
    filteredDropRows() {
      if (this.dropTableFilter === 'all') {
        return this.dropRows;
      }
      return this.dropRows.filter(row => row.table === this.dropTableFilter);
    },
    mainDropWeightTotal() {
      return this.dropRows
        .filter(row => row.table === 'main')
        .reduce((total, row) => total + this.positiveNumber(row.weightText, 1), 0) + this.mainEmptySlots;
    },
    mainEmptySlots() {
      const slots = Number(this.mainEmptySlotsText);
      return Number.isInteger(slots) && slots > 0 ? slots : 0;
    },
    emptyMainChanceLabel() {
      const total = this.mainDropWeightTotal;
      return total > 0 ? `${this.mainEmptySlots}/${total} main (${this.percent(this.mainEmptySlots / total)})` : 'No empty slots';
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
    },
    itemQuery(query) {
      this.scheduleItemSearch(query);
    },
    shopItemQuery(query) {
      this.scheduleShopItemSearch(query);
    }
  },
  created() {
    this.fetchAll();
  },
  beforeDestroy() {
    clearTimeout(this.itemSearchDebounce);
    clearTimeout(this.shopItemSearchDebounce);
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
    scheduleItemSearch(query) {
      clearTimeout(this.itemSearchDebounce);
      const trimmed = query.trim();
      if (!trimmed) {
        this.itemResults = [];
        this.itemSearchLoading = false;
        return;
      }
      if (trimmed.length < 2 && !/^\d+$/.test(trimmed)) {
        return;
      }
      this.itemSearchDebounce = setTimeout(() => this.searchItems(false), 220);
    },
    scheduleShopItemSearch(query) {
      clearTimeout(this.shopItemSearchDebounce);
      const trimmed = query.trim();
      if (!trimmed) {
        this.shopItemResults = [];
        this.shopItemSearchLoading = false;
        return;
      }
      if (trimmed.length < 2 && !/^\d+$/.test(trimmed)) {
        return;
      }
      this.shopItemSearchDebounce = setTimeout(() => this.searchShopItems(false), 220);
    },
    async searchItems(force = true) {
      await this.searchItemOptions({
        queryField: 'itemQuery',
        resultsField: 'itemResults',
        loadingField: 'itemSearchLoading',
        tokenField: 'itemSearchToken',
        force
      });
    },
    async searchShopItems(force = true) {
      await this.searchItemOptions({
        queryField: 'shopItemQuery',
        resultsField: 'shopItemResults',
        loadingField: 'shopItemSearchLoading',
        tokenField: 'shopItemSearchToken',
        force
      });
    },
    async searchItemOptions({ queryField, resultsField, loadingField, tokenField, force = true }) {
      const query = this[queryField].trim();
      if (!query) {
        this[resultsField] = [];
        this[loadingField] = false;
        return;
      }
      if (!force && query.length < 2 && !/^\d+$/.test(query)) {
        return;
      }
      this.error = '';
      const cacheKey = query.toLowerCase();
      const cached = this.itemSearchCache[cacheKey];
      if (cached) {
        this[resultsField] = cached;
        this.status = `Found ${cached.length} item matches.`;
        this[loadingField] = false;
        return;
      }
      const token = ++this[tokenField];
      this[loadingField] = true;
      try {
        const res = await axios.get(`${API}/world-editor/items/search`, { params: { q: query, limit: 80 } });
        if (token !== this[tokenField] || query !== this[queryField].trim()) {
          return;
        }
        const results = res.data.results || [];
        this.rememberItemSearch(cacheKey, results);
        this[resultsField] = results;
        this.status = `Found ${results.length} item matches.`;
      } catch (err) {
        if (token === this[tokenField]) {
          this.error = this.errorMessage(err);
        }
      } finally {
        if (token === this[tokenField]) {
          this[loadingField] = false;
        }
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
      this.mainEmptySlotsText = String(drops.mainEmptySlots || 0);
      this.dropRows = this.rowsFromDrops(drops);
      this.advancedDropsDirty = false;
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
        this.shopRows = [];
        this.advancedShopItemsDirty = false;
        return;
      }
      if (!shop) {
        this.shopDraft = { ...emptyShop(), npcIdsText: this.currentNpcId >= 0 ? String(this.currentNpcId) : '' };
        this.shopRows = [];
        this.advancedShopItemsDirty = false;
        return;
      }
      this.shopDraft = {
        ...shop,
        npcIdsText: (shop.npcIds || []).join(', '),
        notes: shop.notes || '',
        itemsText: this.formatJson(shop.items || [])
      };
      this.shopRows = this.rowsFromShopItems(shop.items || []);
      this.advancedShopItemsDirty = false;
    },
    resetDefinition() {
      this.definitionDraft = emptyDefinition();
      this.shopDraft = emptyShop();
      this.dropText = { always: '[]', main: '[]', preroll: '[]', tertiary: '[]' };
      this.dropRows = [];
      this.shopRows = [];
      this.mainEmptySlotsText = '0';
      this.itemResults = [];
      this.shopItemResults = [];
      this.itemSearchLoading = false;
      this.shopItemSearchLoading = false;
      this.advancedDropsDirty = false;
      this.advancedShopItemsDirty = false;
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
      const drops = this.dropsPayloadFromRows();
      if (!drops) {
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
      const items = this.shopItemsPayloadFromRows();
      if (!items) {
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
    rememberItemSearch(cacheKey, results) {
      this.itemSearchCache[cacheKey] = results;
      const keys = Object.keys(this.itemSearchCache);
      if (keys.length > 50) {
        delete this.itemSearchCache[keys[0]];
      }
    },
    addDropFromItem(item) {
      this.dropRows.push({
        uid: this.nextDropUid++,
        table: this.newDropTable,
        itemIdText: String(item.id),
        name: item.name,
        minAmountText: '1',
        maxAmountText: '1',
        weightText: '1',
        oneInText: this.newDropTable === 'tertiary' ? '128' : '',
        noted: Boolean(item.noted)
      });
      this.itemResults = [];
      this.itemQuery = '';
      this.advancedDropsDirty = false;
    },
    async lookupDropItem(row) {
      const itemId = Number(row.itemIdText);
      if (!Number.isInteger(itemId) || itemId < 0) {
        return;
      }
      try {
        const res = await axios.get(`${API}/world-editor/items/search`, { params: { q: String(itemId), limit: 20 } });
        const match = (res.data.results || []).find(item => Number(item.id) === itemId);
        if (match) {
          row.name = match.name;
          row.noted = Boolean(match.noted);
        }
      } catch (err) {
        this.error = this.errorMessage(err);
      }
    },
    duplicateDrop(row) {
      this.dropRows.push({
        ...row,
        uid: this.nextDropUid++
      });
      this.advancedDropsDirty = false;
    },
    removeDrop(row) {
      this.dropRows = this.dropRows.filter(item => item.uid !== row.uid);
      this.advancedDropsDirty = false;
    },
    addShopItemFromItem(item) {
      this.shopRows.push({
        uid: this.nextShopItemUid++,
        itemIdText: String(item.id),
        name: item.name,
        amountText: '1',
        priceText: '0',
        buyPriceText: '',
        restockTicksText: '',
        noted: Boolean(item.noted)
      });
      this.shopItemResults = [];
      this.shopItemQuery = '';
      this.advancedShopItemsDirty = false;
    },
    async lookupShopItem(row) {
      const itemId = Number(row.itemIdText);
      if (!Number.isInteger(itemId) || itemId < 0) {
        return;
      }
      try {
        const res = await axios.get(`${API}/world-editor/items/search`, { params: { q: String(itemId), limit: 20 } });
        const match = (res.data.results || []).find(item => Number(item.id) === itemId);
        if (match) {
          row.name = match.name;
          row.noted = Boolean(match.noted);
        }
      } catch (err) {
        this.error = this.errorMessage(err);
      }
    },
    duplicateShopItem(row) {
      this.shopRows.push({
        ...row,
        uid: this.nextShopItemUid++
      });
      this.advancedShopItemsDirty = false;
    },
    removeShopItem(row) {
      this.shopRows = this.shopRows.filter(item => item.uid !== row.uid);
      this.advancedShopItemsDirty = false;
    },
    rowsFromDrops(drops = {}) {
      return ['always', 'main', 'tertiary', 'preroll'].flatMap(table => {
        return (drops[table] || []).map(drop => this.rowFromDrop(table, drop));
      });
    },
    rowsFromShopItems(items = []) {
      return (items || []).map(item => this.rowFromShopItem(item));
    },
    rowFromShopItem(item) {
      return {
        uid: this.nextShopItemUid++,
        itemIdText: item.itemId == null ? '' : String(item.itemId),
        name: item.name || '',
        amountText: String(item.amount == null ? 1 : item.amount),
        priceText: String(item.price == null ? 0 : item.price),
        buyPriceText: item.buyPrice == null ? '' : String(item.buyPrice),
        restockTicksText: item.restockTicks == null ? '' : String(item.restockTicks),
        noted: Boolean(item.noted)
      };
    },
    rowFromDrop(table, drop) {
      const oneIn = this.oneInFromDrop(table, drop);
      return {
        uid: this.nextDropUid++,
        table,
        itemIdText: drop.itemId == null ? '' : String(drop.itemId),
        name: drop.name || '',
        minAmountText: String(drop.minAmount || 1),
        maxAmountText: String(drop.maxAmount || drop.minAmount || 1),
        weightText: String(drop.weight || this.oneInFromDrop('main', drop) || 1),
        oneInText: oneIn === '' ? '' : String(oneIn),
        noted: Boolean(drop.noted)
      };
    },
    oneInFromDrop(table, drop) {
      if (!this.usesOneIn(table) && table !== 'main') {
        return '';
      }
      if (drop.denominator) {
        return drop.numerator > 0 ? Math.max(1, Math.round(drop.denominator / drop.numerator)) : drop.denominator;
      }
      if (drop.chance > 0) {
        return Math.max(1, Math.round(1 / drop.chance));
      }
      if (this.usesOneIn(table) && drop.weight) {
        return drop.weight;
      }
      return '';
    },
    dropsPayloadFromRows() {
      const mainEmptySlots = this.requiredPositiveInt(this.mainEmptySlotsText || '0', 'Empty main slots', 0);
      if (mainEmptySlots === null) {
        return null;
      }
      const drops = { always: [], main: [], mainEmptySlots, preroll: [], tertiary: [] };
      for (const row of this.dropRows) {
        const built = this.dropEntryFromRow(row);
        if (!built) {
          return null;
        }
        drops[built.table].push(built.entry);
      }
      this.dropText = {
        always: this.formatJson(drops.always),
        main: this.formatJson(drops.main),
        preroll: this.formatJson(drops.preroll),
        tertiary: this.formatJson(drops.tertiary)
      };
      return drops;
    },
    dropEntryFromRow(row) {
      const table = this.dropTableOptions.some(option => option.key === row.table) ? row.table : 'tertiary';
      const itemId = this.requiredPositiveInt(row.itemIdText, 'Drop item id', 0);
      const minAmount = this.requiredPositiveInt(row.minAmountText, 'Drop minimum amount');
      const maxAmount = row.maxAmountText === '' ? minAmount : this.requiredPositiveInt(row.maxAmountText, 'Drop maximum amount');
      if (itemId === null || minAmount === null || maxAmount === null) {
        return null;
      }
      if (maxAmount < minAmount) {
        this.error = `Drop ${row.name || itemId} has max amount lower than min amount.`;
        return null;
      }
      const entry = {
        itemId,
        name: row.name || null,
        minAmount,
        maxAmount
      };
      if (row.noted) {
        entry.noted = true;
      }
      if (table === 'main') {
        const weight = this.requiredPositiveInt(row.weightText || '1', `Main drop weight for ${row.name || itemId}`);
        if (weight === null) {
          return null;
        }
        entry.weight = weight;
      } else if (this.usesOneIn(table)) {
        const denominator = this.requiredPositiveInt(row.oneInText, `One-in rarity for ${row.name || itemId}`);
        if (denominator === null) {
          return null;
        }
        entry.denominator = denominator;
      }
      return { table, entry };
    },
    shopItemsPayloadFromRows() {
      const items = [];
      for (const row of this.shopRows) {
        const built = this.shopItemEntryFromRow(row);
        if (!built) {
          return null;
        }
        items.push(built);
      }
      this.shopDraft.itemsText = this.formatJson(items);
      return items;
    },
    shopItemEntryFromRow(row) {
      const itemId = this.requiredPositiveInt(row.itemIdText, 'Shop item id', 0);
      const amount = this.requiredPositiveInt(row.amountText || '0', `Shop stock for ${row.name || row.itemIdText}`, 0);
      const price = this.requiredPositiveInt(row.priceText || '0', `Shop sell price for ${row.name || row.itemIdText}`, 0);
      if (itemId === null || amount === null || price === null) {
        return null;
      }
      const entry = {
        itemId,
        name: row.name || null,
        amount,
        price
      };
      if (row.buyPriceText !== '') {
        const buyPrice = this.requiredPositiveInt(row.buyPriceText, `Shop buy price for ${row.name || itemId}`, 0);
        if (buyPrice === null) {
          return null;
        }
        entry.buyPrice = buyPrice;
      }
      if (row.restockTicksText !== '') {
        const restockTicks = this.requiredPositiveInt(row.restockTicksText, `Shop restock ticks for ${row.name || itemId}`, 0);
        if (restockTicks === null) {
          return null;
        }
        entry.restockTicks = restockTicks;
      }
      return entry;
    },
    syncAdvancedDropsFromRows() {
      const drops = this.dropsPayloadFromRows();
      if (drops) {
        this.advancedDropsDirty = false;
        this.status = 'Advanced JSON refreshed from rows.';
      }
    },
    syncDropRowsFromAdvanced() {
      const drops = {
        always: this.parseJsonField(this.dropText.always, 'Always drops JSON'),
        main: this.parseJsonField(this.dropText.main, 'Main drops JSON'),
        preroll: this.parseJsonField(this.dropText.preroll, 'Pre-roll drops JSON'),
        tertiary: this.parseJsonField(this.dropText.tertiary, 'Rare extra drops JSON'),
        mainEmptySlots: this.mainEmptySlots
      };
      if (Object.values(drops).some(value => value === null)) {
        return;
      }
      this.mainEmptySlotsText = String(drops.mainEmptySlots || 0);
      this.dropRows = this.rowsFromDrops(drops);
      this.advancedDropsDirty = false;
      this.status = 'Drop rows synced from advanced JSON.';
    },
    syncAdvancedShopItemsFromRows() {
      const items = this.shopItemsPayloadFromRows();
      if (items) {
        this.advancedShopItemsDirty = false;
        this.status = 'Shop items JSON refreshed from rows.';
      }
    },
    syncShopRowsFromAdvanced() {
      const items = this.parseJsonField(this.shopDraft.itemsText, 'Shop items JSON');
      if (items === null) {
        return;
      }
      if (!Array.isArray(items)) {
        this.error = 'Shop items JSON must be an array.';
        return;
      }
      this.shopRows = this.rowsFromShopItems(items);
      this.advancedShopItemsDirty = false;
      this.status = 'Shop rows synced from advanced JSON.';
    },
    usesOneIn(table) {
      return table === 'tertiary' || table === 'preroll';
    },
    dropChanceLabel(row) {
      if (row.table === 'always') {
        return 'Every kill';
      }
      if (row.table === 'main') {
        const weight = this.positiveNumber(row.weightText, 1);
        const total = this.mainDropWeightTotal || weight;
        return `${weight}/${total} main (${this.percent(weight / total)})`;
      }
      if (this.usesOneIn(row.table)) {
        const oneIn = this.positiveNumber(row.oneInText, 0);
        return oneIn > 0 ? `1/${oneIn} (${this.percent(1 / oneIn)})` : 'Set one-in';
      }
      return '-';
    },
    requiredPositiveInt(value, label, minimum = 1) {
      const number = Number(value);
      if (!Number.isInteger(number) || number < minimum) {
        this.error = `${label} must be an integer greater than or equal to ${minimum}.`;
        return null;
      }
      return number;
    },
    positiveNumber(value, fallback) {
      const number = Number(value);
      return Number.isFinite(number) && number > 0 ? number : fallback;
    },
    percent(value) {
      return `${(value * 100).toFixed(value < 0.01 ? 4 : 2)}%`;
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

.drop-builder {
  display: grid;
  gap: 16px;
}

.drop-help,
.advanced-title {
  display: grid;
  gap: 5px;
  padding: 14px;
  border-radius: 16px;
  background: #f6eddb;
  color: #435044;
}

.drop-help strong,
.advanced-title strong {
  color: #26372d;
}

.drop-toolbar,
.item-results {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 10px;
}

.drop-toolbar label {
  min-width: 170px;
}

.drop-toolbar .search-field {
  flex: 1;
  min-width: 260px;
}

.inline-status {
  align-self: center;
  color: #566157;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.item-results button {
  display: grid;
  gap: 3px;
  background: #81603a;
  text-align: left;
}

.item-results span {
  font-size: 12px;
  opacity: 0.78;
}

.drop-table-wrap {
  overflow-x: auto;
  border: 1px solid rgba(38, 55, 45, 0.14);
  border-radius: 18px;
}

.drop-table {
  width: 100%;
  min-width: 920px;
  border-collapse: collapse;
  background: #fffdf7;
}

.drop-table th,
.drop-table td {
  padding: 12px;
  border-bottom: 1px solid rgba(38, 55, 45, 0.12);
  text-align: left;
  vertical-align: top;
}

.drop-table th {
  background: #efe2c7;
  color: #38463c;
  font-size: 12px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.item-cell,
.amount-cell,
.row-actions {
  display: flex;
  gap: 8px;
}

.item-cell {
  min-width: 260px;
}

.item-cell input:first-child {
  max-width: 100px;
}

.amount-cell {
  align-items: center;
  min-width: 190px;
}

.amount-cell input {
  max-width: 78px;
}

.rarity-static,
.chance-pill {
  display: inline-flex;
  align-items: center;
  min-height: 41px;
  color: #435044;
}

.chance-pill {
  padding: 0 10px;
  border-radius: 999px;
  background: #eef3e8;
  font-size: 12px;
  font-weight: 800;
}

.empty-drops {
  color: #6e786f;
  text-align: center;
}

.empty-main-row {
  background: #faf4e6;
  color: #566157;
  font-weight: 800;
}

.advanced-drops {
  display: grid;
  gap: 14px;
  padding: 14px;
  border: 1px dashed rgba(38, 55, 45, 0.28);
  border-radius: 18px;
  background: rgba(255, 253, 247, 0.72);
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
