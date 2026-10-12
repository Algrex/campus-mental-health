import {createApp} from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import App from './App.vue';
import router from './router/index.js';
import './styles/main.css';

createApp(App).use(router).use(ElementPlus).mount('#app');
