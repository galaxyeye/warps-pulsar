-- noinspection SqlResolveForFile
-- noinspection SqlNoDataSourceInspectionForFile

CREATE TABLE `asin_sync` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
    `category` varchar(255) DEFAULT NULL COMMENT '所属分类nodeID',
    `col1` text DEFAULT NULL COMMENT '预留字段',
    `col2` text DEFAULT NULL,
    `img` text DEFAULT NULL COMMENT '主图URL',
    `smallrank` text DEFAULT NULL COMMENT '小类排名',
    `bigrank` text DEFAULT NULL COMMENT '大类排名',
    `title` text COMMENT '商品标题',
    `desc` text COMMENT '商品描述',
    `asin` varchar(255) DEFAULT NULL COMMENT '商品asin编码',
    `brand` varchar(255) DEFAULT NULL COMMENT '品牌',
    `soldby` text DEFAULT NULL COMMENT '卖家',
    `shipby` text DEFAULT NULL COMMENT '物流',
    `price` text DEFAULT NULL COMMENT '价格',
    `instock` tinyint(1) DEFAULT NULL COMMENT '是否缺货 0 不缺货  1  缺货',
    `isaddcart` tinyint(1) DEFAULT NULL COMMENT '加入购物车按钮',
    `isbuy` tinyint(1) DEFAULT NULL COMMENT '直接购买按钮',
    `isac` tinyint(1) DEFAULT NULL COMMENT '是否amazon精选推荐',
    `isbs` tinyint(1) DEFAULT NULL COMMENT '释放amazon热卖推荐',
    `isa` tinyint(1) DEFAULT NULL COMMENT '是否A+页面',
    `othersellernum` int(11) DEFAULT NULL COMMENT '跟卖数量',
    `qanum` int(11) DEFAULT NULL COMMENT 'QA问题数',
    `score` float DEFAULT NULL COMMENT '平均评星数',
    `reviews` int(11) DEFAULT NULL COMMENT '评论总数',
    `starnum` int(11) DEFAULT NULL COMMENT '评星总数',
    `score5percent` varchar(255) DEFAULT NULL COMMENT '5星级占比',
    `score4percent` varchar(255) DEFAULT NULL COMMENT '4星级占比',
    `score3percent` varchar(255) DEFAULT NULL COMMENT '3星级占比',
    `score2percent` varchar(255) DEFAULT NULL COMMENT '2星级占比',
    `score1percent` varchar(255) DEFAULT NULL COMMENT '1星级占比',
    `weight` varchar(255) DEFAULT NULL COMMENT '重量',
    `volume` varchar(255) DEFAULT NULL COMMENT '体积',
    `isad` tinyint(1) DEFAULT NULL COMMENT '是否列表广告推广',
    `adposition` varchar(255) DEFAULT NULL COMMENT '列表广告位置',
    `commenttime` varchar(255) DEFAULT NULL COMMENT '第一条评论时间',
    `reviewsmention` text COMMENT '高频评论词',
    `onsaletime` varchar(255) DEFAULT NULL COMMENT '上架时间',
    `feedbackurl` text DEFAULT NULL COMMENT '打开feedback页面的URL',
    `sellerID` text DEFAULT NULL COMMENT 'sellerID',
    `marketplaceID` text DEFAULT NULL COMMENT 'marketplaceID',
    `reviewsurl` text DEFAULT NULL COMMENT '打开所有评论页面的URL',
    `sellsameurl` text DEFAULT NULL COMMENT '打开跟卖信息页面的URL',
    `fba_fee` float DEFAULT NULL COMMENT 'FBA运费',
    `createtime` varchar(255) DEFAULT NULL,
    `isvalid` tinyint(1) DEFAULT '1' COMMENT 'valid',
    `categorylevel` text DEFAULT NULL COMMENT '各级分类nodeID',
    `categorypath` text DEFAULT NULL COMMENT '分类路径',
    `categorypathlevel` text DEFAULT NULL COMMENT '各级分类路径',
    `categoryname` text DEFAULT NULL COMMENT '分类名称',
    `categorynamelevel` text DEFAULT NULL COMMENT '各级分类名称',
    `ranklevel` varchar(255) DEFAULT NULL COMMENT '各级排名',
    `brandlink` text DEFAULT NULL COMMENT '品牌链接',
    `listprice` varchar(255) DEFAULT NULL COMMENT '挂牌价格',
    `gallery` text COMMENT '图库',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=963 DEFAULT CHARSET=utf8 COMMENT='商品表-美国';
