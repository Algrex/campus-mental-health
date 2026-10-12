import {createApp} from 'vue';
import {ElDialog, ElDrawer} from 'element-plus';
import 'element-plus/dist/index.css';
import App from './App.vue';
import router from './router/index.js';
import './styles/main.css';
const app = createApp(App);
app
  .component('ElDialog', ElDialog)
  .component('ElDrawer', ElDrawer)
  .use(router)
  .mount('#app');
