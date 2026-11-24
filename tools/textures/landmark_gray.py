PAL = {symbol: (value,) * 3 for symbol, value in zip("abcde", (109, 151, 183, 211, 235))}

TEXES = {
    "block/landmark_brick": [
        "dddddddaddddddda", "dccccccadcccccca", "dccbccbadcbcccba", "dcccccbadcccccba",
        "dcbcccbadccbccba", "dcccccbadcccccba", "dbbbbbbadbbbbbba", "aaaaaaaaaaaaaaaa",
        "dddadddddddadddd", "cccadccccccadccc", "ccbadcbcccbadccc", "ccbadccccccadcbc",
        "ccbadccbccbadccc", "ccbadccccccadccb", "bbbadbbbbbbadbbb", "aaaaaaaaaaaaaaaa",
    ],
    "block/landmark_wood": [
        "eddddddddddddddd", "dddddcccdddddddd", "dddddddcdddddddd", "ddcccddddddddddd",
        "dddddddddcccdddd", "dddddccccddddddd", "cccccbcccccccccc", "aaaaaaaaaaaaaaaa",
        "dedddddddddddddd", "ddddddcccddddddd", "ddddddddddcccddd", "cccddddddddddddd",
        "ddddccccdddddddd", "dddddddddccddddd", "cccccccccccbcccc", "aaaaaaaaaaaaaaaa",
    ],
    "block/landmark_tile": [
        "bcdedcbabcdedcba", "bcdedcbabcdedcba", "bcdedcbabcdedcba", "bcdedcbabcdedcba",
        "bcdedcbabcdedcba", "bcdedcbabcdedcba", "bccdcbbabccdcbba", "aaaaaaaaaaaaaaaa",
        "dcbabcdedcbabcde", "dcbabcdedcbabcde", "dcbabcdedcbabcde", "dcbabcdedcbabcde",
        "dcbabcdedcbabcde", "dcbabcdedcbabcde", "cbbabccdcbbabccd", "aaaaaaaaaaaaaaaa",
    ],
}
