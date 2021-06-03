<template>

<v-data-table
    :headers="headers"
    :items="mypage"
    :items-per-page="5"
    class="elevation-1"
  ></v-data-table>

</template>

<script>
  const axios = require('axios').default;

  export default {
    name: 'Mypage',
    props: {
      value: Object,
      editMode: Boolean,
      isNew: Boolean
    },
    data: () => ({
        headers: [
            { text: "id", value: "id" },
            { text: "productId", value: "productId" },
            { text: "qty", value: "qty" },
            { text: "price", value: "price" },
            { text: "status", value: "status" },
            { text: "deliveryId", value: "deliveryId" },
            { text: "customerId", value: "customerId" },
            { text: "destination", value: "destination" },
            { text: "orderId", value: "orderId" },
            { text: "paymentId", value: "paymentId" },
        ],
        mypage : [],
    }),
    async created() {
      var temp = await axios.get(axios.backend + '/mypages')

      this.mypage = temp.data._embedded.mypages;

    },
    methods: {
    }
  }
</script>

