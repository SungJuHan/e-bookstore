
import Vue from 'vue'
import Router from 'vue-router'

Vue.use(Router);


import OrderManager from "./components/OrderManager"

import OrderManagementManager from "./components/OrderManagementManager"

import PaymentManager from "./components/PaymentManager"

import NotificationManager from "./components/NotificationManager"

import DeliveryManager from "./components/DeliveryManager"


import Mypage from "./components/mypage"
export default new Router({
    // mode: 'history',
    base: process.env.BASE_URL,
    routes: [
            {
                path: '/Order',
                name: 'OrderManager',
                component: OrderManager
            },

            {
                path: '/OrderManagement',
                name: 'OrderManagementManager',
                component: OrderManagementManager
            },

            {
                path: '/Payment',
                name: 'PaymentManager',
                component: PaymentManager
            },

            {
                path: '/Notification',
                name: 'NotificationManager',
                component: NotificationManager
            },

            {
                path: '/Delivery',
                name: 'DeliveryManager',
                component: DeliveryManager
            },


            {
                path: '/mypage',
                name: 'mypage',
                component: mypage
            },


    ]
})
