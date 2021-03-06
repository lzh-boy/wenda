package com.nowcoder.service;

import ch.qos.logback.core.net.SyslogOutputStream;
import org.apache.commons.lang.CharUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.runtime.directive.MacroParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@Service
public class SensitiveService implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveService.class);

    //默认敏感词替换符
    private static final String DEFAULT_RAPLACEMENT = "敏感词";

    private class TreeNode{

        //true 关键词的终结 false 继续
        private boolean end = false;

        //key下一个字符 value是对应的节点
        private Map<Character, TreeNode> subNodes = new HashMap<>();

        //向指定位置添加节点树
        void addSubNode(Character key, TreeNode node){
            subNodes.put(key, node);
        }

        //获取下个节点
        TreeNode getSubNode(Character key){
            return subNodes.get(key);

        }

        boolean isKeywordEnd(){
            return end;
        }

        void setKeywordEnd(boolean end){
            this.end = end;

        }

        public int getSubNodeCount(){
            return subNodes.size();
        }
    }

    //根节点
    private TreeNode rootNode = new TreeNode();
     //判断是否是一个符号
    private boolean isSymbol(char c){
        int ic = (int) c;
        //0x2E80-0x9FFF 东亚文字范围
        return !CharUtils.isAsciiAlphanumeric(c) && (ic<0x2E80 || ic>0x9FFF);
    }

    //过滤敏感词
    public String filter(String text){
        if(StringUtils.isBlank(text))
            return text;

        String replacement = DEFAULT_RAPLACEMENT;
        StringBuilder result = new StringBuilder();

        TreeNode tempNode = rootNode;
        int begin = 0;
        int position = 0;

        while(position<text.length()){
            char c = text.charAt(position);
            //空格直接跳过
            if(isSymbol(c)){
                if(tempNode == rootNode){
                    result.append(c);
                    ++begin;
                }
                ++position;
                continue;
            }

            tempNode = tempNode.getSubNode(c);

            //当前位置的匹配结束
            if(tempNode == null){
                result.append(text.charAt(begin));
                position = begin+1;
                begin = position;
                tempNode = rootNode;
            }else if(tempNode.isKeywordEnd()){
                result.append(replacement);
                position = position+1;
                begin = position;
                tempNode = rootNode;
            }else {
                ++position;
            }
        }

        result.append(text.substring(begin));
        return result.toString();
    }

    private void addWord(String lineText){
        TreeNode tempNode = rootNode;
        //循环每个字符
        for(int i=0; i<lineText.length(); i++){
            Character c = lineText.charAt(i);
            //过滤空格
            if(isSymbol(c))
                continue;

            TreeNode node = tempNode.getSubNode(c);
            if(node==null){
                node = new TreeNode();
                tempNode.addSubNode(c, node);
            }

            tempNode = node;
            if(i==lineText.length()-1)
                tempNode.setKeywordEnd(true);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        rootNode = new TreeNode();
        try {
            InputStream is = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("SensitiveWords.txt");
            InputStreamReader read = new InputStreamReader(is);
            BufferedReader bufferedReader = new BufferedReader(read);
            String lineText;
            while((lineText=bufferedReader.readLine())!=null){
                lineText = lineText.trim();
                addWord(lineText);
            }
            read.close();
        }catch (Exception e){
            logger.error("读取敏感词文件失败"+e.getMessage());
        }
    }

    public static  void main(String[] args){
        SensitiveService s = new SensitiveService();
        s.addWord("色情");
        System.out.println(s.filter("你好，色情"));
    }
}
