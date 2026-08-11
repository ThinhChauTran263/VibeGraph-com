import { createApp } from 'vue'
import { createPinia } from 'pinia'

import './styles/tokens.css'
import App from './App.vue'
import i18n from './language'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(i18n)
app.use(router)

app.mount('#app')
