import {ref, onMounted} from 'vue';
export function useLoad(loader) {
  const busy = ref(true);
  const error = ref('');
  async function reload() {
    busy.value = true;
    error.value = '';
    try {
      await loader();
    } catch (e) {
      error.value = e.message;
    } finally {
      busy.value = false;
    }
  }
  onMounted(reload);
  return {busy, error, reload};
}
