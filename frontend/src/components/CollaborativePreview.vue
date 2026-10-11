<template>
  <CollaborativeEditor :picture-id="pictureId" :image-url="imageUrl" :picture-revision="pictureRevision" @saved="emit('saved')" />
</template>
<script setup lang="ts">
import CollaborativeEditor from './CollaborativeEditor.vue'
defineProps<{ pictureId: string | number; imageUrl: string; pictureRevision: string }>()
const emit = defineEmits<{ saved: [] }>()
</script>
