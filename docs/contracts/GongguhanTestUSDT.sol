// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

/**
 * @title 공구한 테스트용 USDT (Polygon Amoy 전용)
 *
 * @notice ⚠️ 테스트넷 전용입니다. 메인넷에 배포하지 마세요.
 *
 *         누구나 제한 없이 발행(mint)할 수 있습니다. 데모와 테스트에서 구매자 지갑에
 *         토큰을 자유롭게 채우기 위한 의도적인 설계이며, 그래서 이 토큰의 잔액은
 *         아무런 가치를 나타내지 않습니다.
 *
 *         실제 서비스에서는 절대 이런 컨트랙트를 쓰지 않습니다.
 *
 * @dev decimals 를 6 으로 고정한 이유
 *
 *      백엔드 `crypto_assets.decimals` 시드값이 6 입니다. 실제 USDT 가 6 자리를 쓰기
 *      때문에 그 관행을 따랐습니다. OpenZeppelin ERC20 의 기본값은 18 이라 반드시
 *      override 해야 합니다.
 *
 *      이 값이 어긋나면 금액이 10^12 배 틀어집니다. 백엔드는
 *      `CryptoAsset.toRawAmount()` 에서 `사람이 읽는 수량 × 10^decimals` 로 환산하는데,
 *      DB 가 6 이고 컨트랙트가 18 이면 구매자가 보낸 금액이 견적의 100만분의 1 로
 *      계산되어 전부 언더페이로 거부됩니다.
 *
 *      배포 후 `crypto_assets.decimals` 와 이 값이 같은지 반드시 확인하세요.
 */
contract GongguhanTestUSDT is ERC20 {

    uint8 private constant DECIMALS = 6;

    /**
     * @param initialHolder 초기 발행분을 받을 주소. 보통 배포자 지갑을 넣습니다.
     * @param initialSupply 초기 발행량. **최소 단위(raw)로 넣습니다.**
     *                      예) 1,000,000 USDT = 1000000000000 (10^6 × 10^6)
     */
    constructor(address initialHolder, uint256 initialSupply)
        ERC20("Gongguhan Test USDT", "USDT")
    {
        if (initialSupply > 0) {
            _mint(initialHolder, initialSupply);
        }
    }

    /**
     * @notice OpenZeppelin 기본값 18 을 6 으로 덮어씁니다.
     *         백엔드 `crypto_assets.decimals = 6` 과 반드시 일치해야 합니다.
     */
    function decimals() public pure override returns (uint8) {
        return DECIMALS;
    }

    /**
     * @notice ⚠️ 누구나 호출할 수 있습니다. 테스트 전용입니다.
     *
     *         구매자 지갑에 테스트 토큰을 채울 때 씁니다. faucet 을 따로 두지 않고
     *         이 함수로 해결합니다.
     *
     * @param to     받을 주소
     * @param amount 발행량. **최소 단위(raw)로 넣습니다.**
     *               예) 100 USDT = 100000000 (100 × 10^6)
     */
    function mint(address to, uint256 amount) external {
        _mint(to, amount);
    }
}
