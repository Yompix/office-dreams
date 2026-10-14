// Solana libraries for the server (airdrop transfers), bundled into server/vendor/solana.js (see scripts/launch/README.md)
const web3 = require('@solana/web3.js');
const spl = require('@solana/spl-token');
module.exports = { web3, spl };
