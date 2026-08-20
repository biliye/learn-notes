import { defineStore } from 'pinia'
import { getTree } from '../api/catalog'

export const useCatalogStore = defineStore('catalog', {
  state: () => ({
    tree: [],
    loaded: false
  }),
  actions: {
    async load() {
      this.tree = await getTree()
      this.loaded = true
      return this.tree
    },
    async refresh() {
      this.tree = await getTree()
      return this.tree
    }
  }
})
